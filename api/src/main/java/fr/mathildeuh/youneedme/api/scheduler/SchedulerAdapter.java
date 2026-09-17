package fr.mathildeuh.youneedme.api.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Runtime-agnostic scheduling facade.
 *
 * <p>Transparently targets the classic {@code BukkitScheduler} on Paper/Spigot/Purpur, or the
 * regionised {@code GlobalRegionScheduler}/{@code RegionScheduler}/{@code EntityScheduler} on
 * Folia, whichever the server is actually running. Every module in YouNeedMe (and every expansion)
 * MUST go through this instead of touching {@code Bukkit.getScheduler()} or an
 * entity/location-bound Folia scheduler directly - that is the one rule that keeps this plugin
 * Folia-safe.
 *
 * <p>Handles returned by the {@code run*} methods can always be cancelled through {@link
 * ScheduledTask#cancel()} regardless of which backend is actually running underneath.
 */
public interface SchedulerAdapter {

    /** Whether the server this adapter is bound to is running Folia's regionised threading. */
    boolean isRegionised();

    /** Runs a task with no location affinity (config reloads, global broadcasts, shutdown). */
    ScheduledTask runGlobal(Runnable task);

    /** Runs a task with no location affinity after {@code delayTicks}. */
    ScheduledTask runGlobalDelayed(Runnable task, long delayTicks);

    /** Runs a repeating task with no location affinity. */
    ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    /** Runs a task on the region that owns {@code location} (e.g. a block/particle effect). */
    ScheduledTask runAtLocation(Location location, Runnable task);

    /** Runs a task on the region that owns {@code location} after {@code delayTicks}. */
    ScheduledTask runAtLocationDelayed(Location location, Runnable task, long delayTicks);

    /** Runs a repeating task pinned to the region that owns {@code location}. */
    ScheduledTask runAtLocationTimer(
            Location location, Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs a task on the region that currently owns {@code entity}, on the region thread, with the
     * live {@link Entity} handed back through the callback. On Folia this is the ONLY safe way to
     * act on an entity from an async context; on Paper/Spigot it degrades to a normal scheduled
     * task.
     *
     * @param retired invoked instead of {@code task} if the entity is removed/invalid by the time
     *     the task would run (e.g. the player logged off) - never silently dropped
     */
    ScheduledTask runForEntity(
            Entity entity, Consumer<Entity> task, Runnable retired, long delayTicks);

    /** Runs a task off the main/region thread entirely, for blocking I/O (storage, HTTP). */
    ScheduledTask runAsync(Runnable task);

    /** Runs a task off-thread after {@code delayTicks} worth of wall-clock time. */
    ScheduledTask runAsyncDelayed(Runnable task, long delayTicks);

    /** Runs a repeating task off-thread. */
    ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * Convenience wrapper: runs {@code supplier} asynchronously and completes the returned future
     * with its result (or exception), for call sites that want to compose with {@link
     * CompletableFuture} instead of callbacks.
     */
    <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier);

    /** A handle to a task scheduled through this adapter. */
    interface ScheduledTask {
        void cancel();

        boolean isCancelled();
    }
}

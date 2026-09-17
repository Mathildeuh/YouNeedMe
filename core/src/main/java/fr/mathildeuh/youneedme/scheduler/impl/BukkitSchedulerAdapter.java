package fr.mathildeuh.youneedme.scheduler.impl;

import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * {@link SchedulerAdapter} backed by the classic {@code BukkitScheduler} - used on Paper, Spigot
 * and Purpur.
 */
public final class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final Executor asyncExecutor;

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public boolean isRegionised() {
        return false;
    }

    @Override
    public ScheduledTask runGlobal(Runnable task) {
        return wrap(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public ScheduledTask runGlobalDelayed(Runnable task, long delayTicks) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public ScheduledTask runAtLocation(Location location, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public ScheduledTask runAtLocationDelayed(Location location, Runnable task, long delayTicks) {
        return runGlobalDelayed(task, delayTicks);
    }

    @Override
    public ScheduledTask runAtLocationTimer(
            Location location, Runnable task, long delayTicks, long periodTicks) {
        return runGlobalTimer(task, delayTicks, periodTicks);
    }

    @Override
    public ScheduledTask runForEntity(
            Entity entity, Consumer<Entity> task, Runnable retired, long delayTicks) {
        return runGlobalDelayed(
                () -> {
                    if (entity.isValid()) {
                        task.accept(entity);
                    } else if (retired != null) {
                        retired.run();
                    }
                },
                delayTicks);
    }

    @Override
    public ScheduledTask runAsync(Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public ScheduledTask runAsyncDelayed(Runnable task, long delayTicks) {
        return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
    }

    @Override
    public ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(
                Bukkit.getScheduler()
                        .runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    private static ScheduledTask wrap(BukkitTask task) {
        return new ScheduledTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                int id = task.getTaskId();
                // Bukkit has no direct isCancelled(taskId); "neither queued nor running" is the
                // closest equivalent and also covers a one-shot task that already completed.
                return !Bukkit.getScheduler().isQueued(id)
                        && !Bukkit.getScheduler().isCurrentlyRunning(id);
            }
        };
    }
}

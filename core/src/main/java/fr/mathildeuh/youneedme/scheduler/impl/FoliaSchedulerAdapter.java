package fr.mathildeuh.youneedme.scheduler.impl;

import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * {@link SchedulerAdapter} backed by Folia's regionised schedulers. Only ever instantiated when
 * {@link fr.mathildeuh.youneedme.scheduler.ServerEnvironment#isFolia()} is {@code true} - every
 * type referenced here is part of the Paper API surface we already compile against, but calling
 * into it on a non-Folia server throws, so this class must never be constructed otherwise.
 */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private static final long TICK_MILLIS = 50L;

    private final Plugin plugin;
    private final Executor asyncExecutor;

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = task -> Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    @Override
    public boolean isRegionised() {
        return true;
    }

    @Override
    public SchedulerAdapter.ScheduledTask runGlobal(Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, st -> task.run()));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runGlobalDelayed(Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            return runGlobal(task);
        }
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, st -> task.run(), delayTicks));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, st -> task.run(), Math.max(1, delayTicks), periodTicks));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runAtLocation(Location location, Runnable task) {
        return wrap(Bukkit.getRegionScheduler().run(plugin, location, st -> task.run()));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runAtLocationDelayed(Location location, Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            return runAtLocation(location, task);
        }
        return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, location, st -> task.run(), delayTicks));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runAtLocationTimer(
            Location location, Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, location, st -> task.run(), Math.max(1, delayTicks), periodTicks));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runForEntity(
            Entity entity, Consumer<Entity> task, Runnable retired, long delayTicks) {
        Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> callback = st -> task.accept(entity);
        if (delayTicks <= 0) {
            return wrap(entity.getScheduler().run(plugin, callback, retired));
        }
        return wrap(entity.getScheduler().runDelayed(plugin, callback, retired, delayTicks));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runAsync(Runnable task) {
        return wrap(Bukkit.getAsyncScheduler().runNow(plugin, st -> task.run()));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runAsyncDelayed(Runnable task, long delayTicks) {
        return wrap(Bukkit.getAsyncScheduler()
                .runDelayed(plugin, st -> task.run(), Math.max(1, delayTicks) * TICK_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Override
    public SchedulerAdapter.ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getAsyncScheduler()
                .runAtFixedRate(
                        plugin,
                        st -> task.run(),
                        Math.max(1, delayTicks) * TICK_MILLIS,
                        Math.max(1, periodTicks) * TICK_MILLIS,
                        TimeUnit.MILLISECONDS));
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    private static SchedulerAdapter.ScheduledTask wrap(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
        return new SchedulerAdapter.ScheduledTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}

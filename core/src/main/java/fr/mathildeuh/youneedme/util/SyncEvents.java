package fr.mathildeuh.youneedme.util;

import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * Fires a Bukkit event on the main/region thread and hands the (possibly cancelled) event back
 * through a {@link CompletableFuture}, regardless of which thread called {@link #fire}. Services
 * whose mutating methods can be invoked while a previous async operation for the same key is still
 * in flight (see {@code KeyedMutex}) need this to keep "call the cancellable event before doing
 * I/O" actually thread-safe instead of occasionally firing off the main thread.
 */
public final class SyncEvents {

    private SyncEvents() {}

    public static <T extends Event> CompletableFuture<T> fire(SchedulerAdapter scheduler, T event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
            return CompletableFuture.completedFuture(event);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.runGlobal(
                () -> {
                    try {
                        Bukkit.getPluginManager().callEvent(event);
                        future.complete(event);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
        return future;
    }
}

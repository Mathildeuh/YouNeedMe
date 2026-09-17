package fr.mathildeuh.youneedme.util;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Serializes async actions per key so, e.g., two {@code /pay} commands spammed for the same
 * player can never race each other into a double-spend - this is the "verrouillage par joueur"
 * the brief calls for on economy/auction/shop transactions. Each action for a given key only
 * starts once the previous one for that same key has finished (success or failure); different
 * keys run fully in parallel.
 */
public final class KeyedMutex<K> {

    private final Map<K, CompletableFuture<?>> tails = new ConcurrentHashMap<>();

    public <T> CompletableFuture<T> runExclusive(K key, Supplier<CompletableFuture<T>> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        tails.compute(key, (k, previousTail) -> {
            CompletableFuture<Void> previous =
                    previousTail == null ? CompletableFuture.completedFuture(null) : previousTail.handle((r, ex) -> null);
            CompletableFuture<Void> newTail = previous.thenCompose(ignored -> action.get())
                    .handle((value, ex) -> {
                        if (ex != null) {
                            result.completeExceptionally(ex instanceof java.util.concurrent.CompletionException ce
                                    ? ce.getCause()
                                    : ex);
                        } else {
                            result.complete(value);
                        }
                        return null;
                    });
            return newTail;
        });
        tails.computeIfPresent(key, (k, currentTail) -> {
            currentTail.whenComplete((v, ex) -> tails.remove(key, currentTail));
            return currentTail;
        });
        return result;
    }
}

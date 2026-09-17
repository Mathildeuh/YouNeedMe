package fr.mathildeuh.youneedme.api.storage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ShopRepository {

    CompletableFuture<Optional<Integer>> getStock(String categoryId, String itemId);

    CompletableFuture<Void> setStock(String categoryId, String itemId, int stock);

    /**
     * Atomically adjusts stock by {@code delta} (negative on buy, positive on sell-back) and
     * returns the new value.
     */
    CompletableFuture<Integer> adjustStock(String categoryId, String itemId, int delta);
}

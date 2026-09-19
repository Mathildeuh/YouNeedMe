package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.playershop.PlayerShop;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerShopRepository {

    CompletableFuture<PlayerShop> save(PlayerShop shop);

    CompletableFuture<Void> delete(long id);

    /** Every shop, for warming {@code PlayerShopService}'s in-memory sign/chest lookup caches. */
    CompletableFuture<List<PlayerShop>> findAllShops();

    CompletableFuture<List<PlayerShop>> findShopsByOwner(UUID owner);
}

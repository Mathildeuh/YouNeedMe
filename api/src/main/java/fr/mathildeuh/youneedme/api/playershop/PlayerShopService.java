package fr.mathildeuh.youneedme.api.playershop;

import fr.mathildeuh.youneedme.api.model.Position;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Chest+sign player shops: create one, trade against it, remove it. Every lookup here is
 * synchronous and backed by an in-memory cache (there is no async "does a shop exist here" check a
 * block-interact/break listener could reasonably wait on), kept in sync with storage on every
 * mutation.
 */
public interface PlayerShopService {

    /** The shop whose sign is at this exact block position, if any. */
    Optional<PlayerShop> atSign(Position signLocation);

    /** The shop whose stock chest is at this exact block position, if any. */
    Optional<PlayerShop> atChest(Position chestLocation);

    List<PlayerShop> shopsByOwner(UUID owner);

    CompletableFuture<PlayerShop> create(
            UUID owner,
            Position signLocation,
            Position chestLocation,
            ItemStack item,
            @Nullable Double buyPrice,
            @Nullable Double sellPrice);

    CompletableFuture<Void> remove(long shopId);

    CompletableFuture<TradeResult> buy(UUID buyer, long shopId, int amount);

    CompletableFuture<TradeResult> sell(UUID seller, long shopId, int amount);

    enum TradeResult {
        SUCCESS,
        SHOP_NOT_FOUND,
        NOT_BUYABLE,
        NOT_SELLABLE,
        /** The chest block is gone/changed without going through proper shop removal. */
        CHEST_MISSING,
        OUT_OF_STOCK,
        CHEST_FULL,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL,
        CANNOT_TRADE_OWN_SHOP
    }
}

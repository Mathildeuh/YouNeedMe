package fr.mathildeuh.youneedme.api.shop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

/** The server shop: fixed-price buy/sell against configured categories, plus quick-sell/worth. */
public interface ShopService {

    List<ShopCategory> categories();

    Optional<ShopCategory> category(String id);

    Optional<ShopItem> item(String itemId);

    CompletableFuture<TradeResult> buy(UUID player, String itemId, int amount);

    CompletableFuture<TradeResult> sell(UUID player, String itemId, int amount);

    /** Sells every sellable item in the player's hand/inventory at the shop's configured price. */
    CompletableFuture<QuickSellResult> quickSell(UUID player, boolean wholeInventory);

    /** Read-only valuation, no transaction - what {@code /worth} reports. */
    double worth(ItemStack stack);

    CompletableFuture<Void> reload();

    enum TradeResult {
        SUCCESS,
        SHOP_DISABLED,
        ITEM_NOT_FOUND,
        NOT_BUYABLE,
        NOT_SELLABLE,
        OUT_OF_STOCK,
        INSUFFICIENT_STOCK_REQUESTED,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_ITEMS,
        INVENTORY_FULL
    }

    record QuickSellResult(int itemsSold, double totalWorth) {}
}

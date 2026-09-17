package fr.mathildeuh.youneedme.api.shop;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One shop entry. {@code buyPrice}/{@code sellPrice} of {@code null} disables that direction
 * (an item that can only be sold to the shop, or only bought from it). {@code stock} of
 * {@code null} means unlimited.
 */
public record ShopItem(
        String id, ItemStack display, @Nullable Double buyPrice, @Nullable Double sellPrice, @Nullable Integer stock) {

    public boolean isBuyable() {
        return buyPrice != null && (stock == null || stock > 0);
    }

    public boolean isSellable() {
        return sellPrice != null;
    }
}

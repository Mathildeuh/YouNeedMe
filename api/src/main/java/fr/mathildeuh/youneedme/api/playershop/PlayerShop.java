package fr.mathildeuh.youneedme.api.playershop;

import fr.mathildeuh.youneedme.api.model.Position;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A player-run shop: a sign (the trade point) attached to a chest (the stock). {@code buyPrice} is
 * what a visitor pays to take one unit from the chest; {@code sellPrice} is what the owner pays a
 * visitor to add one unit to it. Either may be {@code null} to disable that direction, but not
 * both.
 */
public record PlayerShop(
        long id,
        UUID owner,
        String ownerLastKnownUsername,
        Position signLocation,
        Position chestLocation,
        ItemStack item,
        @Nullable Double buyPrice,
        @Nullable Double sellPrice) {

    public boolean isBuyable() {
        return buyPrice != null;
    }

    public boolean isSellable() {
        return sellPrice != null;
    }
}

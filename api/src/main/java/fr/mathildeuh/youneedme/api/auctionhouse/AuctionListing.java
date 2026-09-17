package fr.mathildeuh.youneedme.api.auctionhouse;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public record AuctionListing(
        long id,
        UUID seller,
        String sellerLastKnownUsername,
        ItemStack item,
        double price,
        long listedAt,
        long expiresAt,
        Status status,
        @Nullable UUID buyer) {

    public enum Status {
        ACTIVE,
        SOLD,
        EXPIRED,
        CANCELLED
    }

    public boolean isActive() {
        return status == Status.ACTIVE && expiresAt > System.currentTimeMillis();
    }
}

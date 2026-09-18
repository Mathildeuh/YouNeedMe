package fr.mathildeuh.youneedme.api.auctionhouse;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A single listing, either fixed-price or an auction. For an auction listing ({@code auction ==
 * true}), {@code price} is the starting/minimum bid and {@code currentBid}/{@code currentBidder}
 * track the highest bid so far (both null until the first bid). A fixed-price listing never touches
 * those three fields.
 */
public record AuctionListing(
        long id,
        UUID seller,
        String sellerLastKnownUsername,
        ItemStack item,
        double price,
        long listedAt,
        long expiresAt,
        Status status,
        @Nullable UUID buyer,
        boolean auction,
        @Nullable Double currentBid,
        @Nullable UUID currentBidder,
        @Nullable String currentBidderUsername) {

    public enum Status {
        ACTIVE,
        SOLD,
        /** An auction ended with a winning bid - the winner still needs to collect the item. */
        WON,
        EXPIRED,
        CANCELLED
    }

    public boolean isActive() {
        return status == Status.ACTIVE && expiresAt > System.currentTimeMillis();
    }

    /** The price a buyer would pay right now: the current bid if any, else the starting price. */
    public double effectivePrice() {
        return currentBid != null ? currentBid : price;
    }
}

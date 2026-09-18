package fr.mathildeuh.youneedme.api.auctionhouse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

/**
 * Player-to-player marketplace: list an item at a fixed price, or as an auction with bidding,
 * optionally taxed on listing.
 */
public interface AuctionHouseService {

    CompletableFuture<AuctionListing> list(UUID seller, ItemStack item, double price);

    /** Lists {@code item} as an auction starting at {@code startingBid}. */
    CompletableFuture<AuctionListing> listAuction(UUID seller, ItemStack item, double startingBid);

    CompletableFuture<PurchaseResult> purchase(UUID buyer, long listingId);

    /**
     * Places a bid on an auction listing. Charges {@code amount} immediately and refunds the
     * previous highest bidder (if any) in the same operation, so a bidder's money is never at risk
     * of vanishing between an outbid and the refund - both happen atomically under the listing's
     * lock.
     */
    CompletableFuture<BidResult> bid(UUID bidder, long listingId, double amount);

    CompletableFuture<Boolean> cancel(UUID seller, long listingId);

    /** Active listings, newest first. */
    CompletableFuture<List<AuctionListing>> browse(int page, int pageSize);

    CompletableFuture<List<AuctionListing>> listingsBySeller(UUID seller, boolean activeOnly);

    /** Listings the player has something waiting on - see {@link #collectExpired}. */
    CompletableFuture<List<AuctionListing>> expiredAwaitingCollection(UUID seller);

    /**
     * Collects a pending item: an unsold/expired fixed-price listing (as its seller) or a won
     * auction (as the winning bidder). Returns {@code false} if there's nothing to collect, the
     * caller isn't the right party, or their inventory has no space.
     */
    CompletableFuture<Boolean> collectExpired(UUID seller, long listingId);

    enum PurchaseResult {
        SUCCESS,
        LISTING_NOT_FOUND,
        LISTING_NOT_ACTIVE,
        LISTING_EXPIRED,
        CANNOT_BUY_OWN_LISTING,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL
    }

    enum BidResult {
        SUCCESS,
        LISTING_NOT_FOUND,
        NOT_AN_AUCTION,
        LISTING_NOT_ACTIVE,
        LISTING_EXPIRED,
        CANNOT_BID_OWN_LISTING,
        BID_TOO_LOW,
        INSUFFICIENT_FUNDS
    }
}

package fr.mathildeuh.youneedme.api.auctionhouse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

/** Player-to-player marketplace: list an item for a fixed price, optionally taxed on listing. */
public interface AuctionHouseService {

    CompletableFuture<AuctionListing> list(UUID seller, ItemStack item, double price);

    CompletableFuture<PurchaseResult> purchase(UUID buyer, long listingId);

    CompletableFuture<Boolean> cancel(UUID seller, long listingId);

    /** Active listings, newest first. */
    CompletableFuture<List<AuctionListing>> browse(int page, int pageSize);

    CompletableFuture<List<AuctionListing>> listingsBySeller(UUID seller, boolean activeOnly);

    /** Listings that expired unsold and are waiting for the seller to reclaim the item. */
    CompletableFuture<List<AuctionListing>> expiredAwaitingCollection(UUID seller);

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
}

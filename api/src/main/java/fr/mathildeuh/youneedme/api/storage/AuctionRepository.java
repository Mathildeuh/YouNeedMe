package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuctionRepository {

    CompletableFuture<AuctionListing> save(AuctionListing listing);

    CompletableFuture<Void> update(AuctionListing listing);

    CompletableFuture<Optional<AuctionListing>> find(long id);

    CompletableFuture<List<AuctionListing>> findActive(int offset, int limit);

    CompletableFuture<List<AuctionListing>> findBySeller(UUID seller, boolean activeOnly);

    /**
     * Listings a player has something waiting on: their own unsold/expired fixed-price listings
     * (item to reclaim) plus any auction they won (item to collect, seller already paid at
     * resolution time).
     */
    CompletableFuture<List<AuctionListing>> findAwaitingCollection(UUID player);

    /** Every ACTIVE listing whose {@code expiresAt} is at or before {@code now}. */
    CompletableFuture<List<AuctionListing>> findActiveExpired(long now);
}

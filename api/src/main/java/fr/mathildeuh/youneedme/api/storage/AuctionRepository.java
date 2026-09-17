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

    CompletableFuture<List<AuctionListing>> findExpiredAwaitingCollection(UUID seller);

    /** Flips every active listing whose {@code expiresAt} has passed to {@code EXPIRED}; returns how many. */
    CompletableFuture<Integer> expireOverdue();
}

package fr.mathildeuh.youneedme.modules.auctionhouse;

import fr.mathildeuh.youneedme.api.auctionhouse.AuctionHouseService;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.event.AuctionListEvent;
import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.storage.AuctionRepository;
import fr.mathildeuh.youneedme.util.KeyedMutex;
import fr.mathildeuh.youneedme.util.SyncEvents;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionHouseServiceImpl implements AuctionHouseService {

    private final AuctionRepository repository;
    private final EconomyService economy;
    private final SchedulerAdapter scheduler;
    private final long defaultDurationMillis;
    private final double listingTaxRate;
    private final KeyedMutex<Long> listingMutex = new KeyedMutex<>();

    public AuctionHouseServiceImpl(
            AuctionRepository repository,
            EconomyService economy,
            SchedulerAdapter scheduler,
            ConfigurationSection config) {
        this.repository = repository;
        this.economy = economy;
        this.scheduler = scheduler;
        this.defaultDurationMillis = config.getLong("listing-duration-hours", 72) * 3_600_000L;
        this.listingTaxRate = config.getDouble("listing-tax-rate", 0.0);
    }

    @Override
    public CompletableFuture<AuctionListing> list(UUID seller, ItemStack item, double price) {
        return createListing(seller, item, price, false);
    }

    @Override
    public CompletableFuture<AuctionListing> listAuction(
            UUID seller, ItemStack item, double startingBid) {
        return createListing(seller, item, startingBid, true);
    }

    private CompletableFuture<AuctionListing> createListing(
            UUID seller, ItemStack item, double price, boolean auction) {
        Player player = Bukkit.getPlayer(seller);
        if (player == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Seller must be online"));
        }
        AuctionListEvent event = new AuctionListEvent(player, item, price);
        return SyncEvents.fire(scheduler, event)
                .thenCompose(
                        fired -> {
                            if (fired.isCancelled()) {
                                return CompletableFuture.failedFuture(
                                        new IllegalStateException("Cancelled by another plugin"));
                            }
                            double tax = fired.getPrice() * listingTaxRate;
                            CompletableFuture<Void> taxCharge =
                                    tax > 0
                                            ? economy.withdraw(seller, tax).thenAccept(result -> {})
                                            : CompletableFuture.completedFuture(null);
                            return taxCharge.thenCompose(
                                    v -> {
                                        long now = System.currentTimeMillis();
                                        AuctionListing listing =
                                                new AuctionListing(
                                                        0,
                                                        seller,
                                                        player.getName(),
                                                        item,
                                                        fired.getPrice(),
                                                        now,
                                                        now + defaultDurationMillis,
                                                        AuctionListing.Status.ACTIVE,
                                                        null,
                                                        auction,
                                                        null,
                                                        null,
                                                        null);
                                        return repository.save(listing);
                                    });
                        });
    }

    @Override
    public CompletableFuture<PurchaseResult> purchase(UUID buyer, long listingId) {
        return listingMutex.runExclusive(
                listingId,
                () ->
                        repository
                                .find(listingId)
                                .thenCompose(
                                        opt -> {
                                            if (opt.isEmpty()) {
                                                return CompletableFuture.completedFuture(
                                                        PurchaseResult.LISTING_NOT_FOUND);
                                            }
                                            AuctionListing listing = opt.get();
                                            if (listing.auction()) {
                                                return CompletableFuture.completedFuture(
                                                        PurchaseResult.LISTING_NOT_ACTIVE);
                                            }
                                            if (listing.status() != AuctionListing.Status.ACTIVE) {
                                                return CompletableFuture.completedFuture(
                                                        PurchaseResult.LISTING_NOT_ACTIVE);
                                            }
                                            if (listing.expiresAt() <= System.currentTimeMillis()) {
                                                return repository
                                                        .update(
                                                                withStatus(
                                                                        listing,
                                                                        AuctionListing.Status
                                                                                .EXPIRED))
                                                        .thenApply(
                                                                v ->
                                                                        PurchaseResult
                                                                                .LISTING_EXPIRED);
                                            }
                                            if (listing.seller().equals(buyer)) {
                                                return CompletableFuture.completedFuture(
                                                        PurchaseResult.CANNOT_BUY_OWN_LISTING);
                                            }
                                            Player buyerPlayer = Bukkit.getPlayer(buyer);
                                            if (buyerPlayer != null
                                                    && !hasSpaceFor(buyerPlayer, listing.item())) {
                                                return CompletableFuture.completedFuture(
                                                        PurchaseResult.INVENTORY_FULL);
                                            }
                                            return economy.transferAtomic(
                                                            buyer,
                                                            listing.seller(),
                                                            listing.price())
                                                    .thenCompose(
                                                            result -> {
                                                                if (!result.isSuccess()) {
                                                                    return CompletableFuture
                                                                            .completedFuture(
                                                                                    PurchaseResult
                                                                                            .INSUFFICIENT_FUNDS);
                                                                }
                                                                AuctionListing sold =
                                                                        withBuyer(
                                                                                withStatus(
                                                                                        listing,
                                                                                        AuctionListing
                                                                                                .Status
                                                                                                .SOLD),
                                                                                buyer);
                                                                return repository
                                                                        .update(sold)
                                                                        .thenApply(
                                                                                v -> {
                                                                                    if (buyerPlayer
                                                                                            != null) {
                                                                                        buyerPlayer
                                                                                                .getInventory()
                                                                                                .addItem(
                                                                                                        listing.item()
                                                                                                                .clone());
                                                                                    }
                                                                                    return PurchaseResult
                                                                                            .SUCCESS;
                                                                                });
                                                            });
                                        }));
    }

    @Override
    public CompletableFuture<BidResult> bid(UUID bidder, long listingId, double amount) {
        return listingMutex.runExclusive(
                listingId,
                () ->
                        repository
                                .find(listingId)
                                .thenCompose(
                                        opt -> {
                                            if (opt.isEmpty()) {
                                                return CompletableFuture.completedFuture(
                                                        BidResult.LISTING_NOT_FOUND);
                                            }
                                            AuctionListing listing = opt.get();
                                            if (!listing.auction()) {
                                                return CompletableFuture.completedFuture(
                                                        BidResult.NOT_AN_AUCTION);
                                            }
                                            if (listing.status() != AuctionListing.Status.ACTIVE) {
                                                return CompletableFuture.completedFuture(
                                                        BidResult.LISTING_NOT_ACTIVE);
                                            }
                                            if (listing.expiresAt() <= System.currentTimeMillis()) {
                                                // applyExpiry, not resolveExpiredAuction: we are
                                                // already inside listingMutex's exclusive block for
                                                // this id, and that method re-acquires the same key
                                                // - which would never complete (the reacquisition
                                                // waits for this very call to finish first).
                                                return applyExpiry(listing)
                                                        .thenApply(v -> BidResult.LISTING_EXPIRED);
                                            }
                                            if (listing.seller().equals(bidder)) {
                                                return CompletableFuture.completedFuture(
                                                        BidResult.CANNOT_BID_OWN_LISTING);
                                            }
                                            // The very first bid may equal the starting price;
                                            // every bid after that must strictly beat the current
                                            // high bid.
                                            boolean validAmount =
                                                    listing.currentBid() != null
                                                            ? amount > listing.currentBid()
                                                            : amount >= listing.price();
                                            if (!validAmount) {
                                                return CompletableFuture.completedFuture(
                                                        BidResult.BID_TOO_LOW);
                                            }
                                            Player bidderPlayer = Bukkit.getPlayer(bidder);
                                            String bidderName =
                                                    bidderPlayer != null
                                                            ? bidderPlayer.getName()
                                                            : String.valueOf(bidder);
                                            UUID previousBidder = listing.currentBidder();
                                            Double previousBid = listing.currentBid();
                                            return economy.withdraw(bidder, amount)
                                                    .thenCompose(
                                                            result -> {
                                                                if (!result.isSuccess()) {
                                                                    return CompletableFuture
                                                                            .completedFuture(
                                                                                    BidResult
                                                                                            .INSUFFICIENT_FUNDS);
                                                                }
                                                                CompletableFuture<Void> refund =
                                                                        previousBidder != null
                                                                                ? economy.deposit(
                                                                                                previousBidder,
                                                                                                previousBid)
                                                                                        .thenAccept(
                                                                                                r -> {})
                                                                                : CompletableFuture
                                                                                        .completedFuture(
                                                                                                null);
                                                                return refund.thenCompose(
                                                                        v ->
                                                                                repository
                                                                                        .update(
                                                                                                withBid(
                                                                                                        listing,
                                                                                                        amount,
                                                                                                        bidder,
                                                                                                        bidderName))
                                                                                        .thenApply(
                                                                                                v2 ->
                                                                                                        BidResult
                                                                                                                .SUCCESS));
                                                            });
                                        }));
    }

    @Override
    public CompletableFuture<Boolean> cancel(UUID seller, long listingId) {
        return listingMutex.runExclusive(
                listingId,
                () ->
                        repository
                                .find(listingId)
                                .thenCompose(
                                        opt -> {
                                            if (opt.isEmpty()
                                                    || !opt.get().seller().equals(seller)
                                                    || opt.get().status()
                                                            != AuctionListing.Status.ACTIVE) {
                                                return CompletableFuture.completedFuture(false);
                                            }
                                            AuctionListing listing = opt.get();
                                            // A bid already committed a bidder's money - refund it
                                            // rather than let a cancel strand that payment.
                                            CompletableFuture<Void> refund =
                                                    listing.currentBidder() != null
                                                            ? economy.deposit(
                                                                            listing.currentBidder(),
                                                                            listing.currentBid())
                                                                    .thenAccept(r -> {})
                                                            : CompletableFuture.completedFuture(
                                                                    null);
                                            return refund.thenCompose(
                                                    v ->
                                                            repository
                                                                    .update(
                                                                            withStatus(
                                                                                    listing,
                                                                                    AuctionListing
                                                                                            .Status
                                                                                            .CANCELLED))
                                                                    .thenApply(
                                                                            v2 -> {
                                                                                Player player =
                                                                                        Bukkit
                                                                                                .getPlayer(
                                                                                                        seller);
                                                                                if (player
                                                                                        != null) {
                                                                                    player.getInventory()
                                                                                            .addItem(
                                                                                                    listing.item()
                                                                                                            .clone());
                                                                                }
                                                                                return true;
                                                                            }));
                                        }));
    }

    @Override
    public CompletableFuture<List<AuctionListing>> browse(int page, int pageSize) {
        return repository.findActive(Math.max(0, page - 1) * pageSize, pageSize);
    }

    @Override
    public CompletableFuture<List<AuctionListing>> listingsBySeller(
            UUID seller, boolean activeOnly) {
        return repository.findBySeller(seller, activeOnly);
    }

    @Override
    public CompletableFuture<List<AuctionListing>> expiredAwaitingCollection(UUID player) {
        return repository.findAwaitingCollection(player);
    }

    @Override
    public CompletableFuture<Boolean> collectExpired(UUID player, long listingId) {
        return listingMutex.runExclusive(
                listingId,
                () ->
                        repository
                                .find(listingId)
                                .thenCompose(
                                        opt -> {
                                            if (opt.isEmpty()) {
                                                return CompletableFuture.completedFuture(false);
                                            }
                                            AuctionListing listing = opt.get();
                                            boolean sellerReclaimingExpired =
                                                    listing.seller().equals(player)
                                                            && listing.status()
                                                                    == AuctionListing.Status
                                                                            .EXPIRED;
                                            boolean winnerCollecting =
                                                    player.equals(listing.buyer())
                                                            && listing.status()
                                                                    == AuctionListing.Status.WON;
                                            if (!sellerReclaimingExpired && !winnerCollecting) {
                                                return CompletableFuture.completedFuture(false);
                                            }
                                            Player onlinePlayer = Bukkit.getPlayer(player);
                                            if (onlinePlayer != null
                                                    && !hasSpaceFor(onlinePlayer, listing.item())) {
                                                return CompletableFuture.completedFuture(false);
                                            }
                                            AuctionListing.Status finalStatus =
                                                    sellerReclaimingExpired
                                                            ? AuctionListing.Status.CANCELLED
                                                            : AuctionListing.Status.SOLD;
                                            return repository
                                                    .update(withStatus(listing, finalStatus))
                                                    .thenApply(
                                                            v -> {
                                                                if (onlinePlayer != null) {
                                                                    onlinePlayer
                                                                            .getInventory()
                                                                            .addItem(
                                                                                    listing.item()
                                                                                            .clone());
                                                                }
                                                                return true;
                                                            });
                                        }));
    }

    /**
     * Called periodically by the module's own timer task. Fixed-price listings and bidless auctions
     * just flip to EXPIRED (seller reclaims via {@link #collectExpired}); an auction with a winning
     * bid pays the seller immediately and moves to WON (winner collects the item via the same
     * method) - resolving it here rather than lazily on the winner's next collection attempt means
     * the seller is paid as soon as the auction actually ends, not whenever the winner next happens
     * to open the AH.
     */
    public CompletableFuture<Integer> expireOverdue() {
        return repository
                .findActiveExpired(System.currentTimeMillis())
                .thenCompose(
                        expired -> {
                            List<CompletableFuture<Void>> resolutions =
                                    expired.stream().map(this::resolveExpiredAuction).toList();
                            return CompletableFuture.allOf(
                                            resolutions.toArray(CompletableFuture[]::new))
                                    .thenApply(v -> expired.size());
                        });
    }

    /** Acquires the listing's lock itself - only for callers not already holding it (the sweep). */
    private CompletableFuture<Void> resolveExpiredAuction(AuctionListing listing) {
        return listingMutex.runExclusive(
                listing.id(),
                () ->
                        repository
                                .find(listing.id())
                                .thenCompose(
                                        opt -> {
                                            if (opt.isEmpty()
                                                    || opt.get().status()
                                                            != AuctionListing.Status.ACTIVE) {
                                                // Already resolved by a concurrent call (e.g. a
                                                // bid attempt that noticed the expiry first).
                                                return CompletableFuture.completedFuture(null);
                                            }
                                            return applyExpiry(opt.get());
                                        }));
    }

    /**
     * The actual resolution logic, with no locking of its own - callers already holding {@code
     * listingMutex} for this listing (e.g. {@link #bid}'s own expiry check) must call this
     * directly, never {@link #resolveExpiredAuction}, which would try to re-acquire the same lock
     * and never complete.
     */
    private CompletableFuture<Void> applyExpiry(AuctionListing current) {
        if (current.auction() && current.currentBidder() != null) {
            return economy.deposit(current.seller(), current.currentBid())
                    .thenCompose(
                            r ->
                                    repository.update(
                                            withBuyer(
                                                    withStatus(current, AuctionListing.Status.WON),
                                                    current.currentBidder())));
        }
        return repository.update(withStatus(current, AuctionListing.Status.EXPIRED));
    }

    private static boolean hasSpaceFor(Player player, ItemStack item) {
        return player.getInventory().firstEmpty() != -1
                || java.util.Arrays.stream(player.getInventory().getStorageContents())
                        .anyMatch(
                                slot ->
                                        slot != null
                                                && slot.isSimilar(item)
                                                && slot.getAmount() < slot.getMaxStackSize());
    }

    private static AuctionListing withStatus(AuctionListing listing, AuctionListing.Status status) {
        return new AuctionListing(
                listing.id(),
                listing.seller(),
                listing.sellerLastKnownUsername(),
                listing.item(),
                listing.price(),
                listing.listedAt(),
                listing.expiresAt(),
                status,
                listing.buyer(),
                listing.auction(),
                listing.currentBid(),
                listing.currentBidder(),
                listing.currentBidderUsername());
    }

    private static AuctionListing withBuyer(AuctionListing listing, UUID buyer) {
        return new AuctionListing(
                listing.id(),
                listing.seller(),
                listing.sellerLastKnownUsername(),
                listing.item(),
                listing.price(),
                listing.listedAt(),
                listing.expiresAt(),
                listing.status(),
                buyer,
                listing.auction(),
                listing.currentBid(),
                listing.currentBidder(),
                listing.currentBidderUsername());
    }

    private static AuctionListing withBid(
            AuctionListing listing, double amount, UUID bidder, String bidderName) {
        return new AuctionListing(
                listing.id(),
                listing.seller(),
                listing.sellerLastKnownUsername(),
                listing.item(),
                listing.price(),
                listing.listedAt(),
                listing.expiresAt(),
                listing.status(),
                listing.buyer(),
                listing.auction(),
                amount,
                bidder,
                bidderName);
    }
}

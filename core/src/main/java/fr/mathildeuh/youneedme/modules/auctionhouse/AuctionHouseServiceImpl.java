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
                                                                        new AuctionListing(
                                                                                listing.id(),
                                                                                listing.seller(),
                                                                                listing
                                                                                        .sellerLastKnownUsername(),
                                                                                listing.item(),
                                                                                listing.price(),
                                                                                listing.listedAt(),
                                                                                listing.expiresAt(),
                                                                                AuctionListing
                                                                                        .Status
                                                                                        .SOLD,
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
                                            return repository
                                                    .update(
                                                            withStatus(
                                                                    opt.get(),
                                                                    AuctionListing.Status
                                                                            .CANCELLED))
                                                    .thenApply(
                                                            v -> {
                                                                Player player =
                                                                        Bukkit.getPlayer(seller);
                                                                if (player != null) {
                                                                    player.getInventory()
                                                                            .addItem(
                                                                                    opt.get()
                                                                                            .item()
                                                                                            .clone());
                                                                }
                                                                return true;
                                                            });
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
    public CompletableFuture<List<AuctionListing>> expiredAwaitingCollection(UUID seller) {
        return repository.findExpiredAwaitingCollection(seller);
    }

    @Override
    public CompletableFuture<Boolean> collectExpired(UUID seller, long listingId) {
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
                                                            != AuctionListing.Status.EXPIRED) {
                                                return CompletableFuture.completedFuture(false);
                                            }
                                            Player player = Bukkit.getPlayer(seller);
                                            if (player != null
                                                    && !hasSpaceFor(player, opt.get().item())) {
                                                return CompletableFuture.completedFuture(false);
                                            }
                                            return repository
                                                    .update(
                                                            withStatus(
                                                                    opt.get(),
                                                                    AuctionListing.Status
                                                                            .CANCELLED))
                                                    .thenApply(
                                                            v -> {
                                                                if (player != null) {
                                                                    player.getInventory()
                                                                            .addItem(
                                                                                    opt.get()
                                                                                            .item()
                                                                                            .clone());
                                                                }
                                                                return true;
                                                            });
                                        }));
    }

    /** Called periodically by the module's own timer task to flip overdue listings. */
    public CompletableFuture<Integer> expireOverdue() {
        return repository.expireOverdue();
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
                listing.buyer());
    }
}

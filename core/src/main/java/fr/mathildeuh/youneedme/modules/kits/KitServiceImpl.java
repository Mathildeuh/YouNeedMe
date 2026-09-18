package fr.mathildeuh.youneedme.modules.kits;

import fr.mathildeuh.youneedme.api.event.KitClaimEvent;
import fr.mathildeuh.youneedme.api.kits.Kit;
import fr.mathildeuh.youneedme.api.kits.KitClaimState;
import fr.mathildeuh.youneedme.api.kits.KitService;
import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.storage.KitRepository;
import fr.mathildeuh.youneedme.util.SyncEvents;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class KitServiceImpl implements KitService {

    private final KitRepository repository;
    private final SchedulerAdapter scheduler;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>(new LinkedHashMap<>());

    public KitServiceImpl(KitRepository repository, SchedulerAdapter scheduler) {
        this.repository = repository;
        this.scheduler = scheduler;
    }

    @Override
    public Collection<Kit> kits() {
        return kits.values();
    }

    @Override
    public Optional<Kit> kit(String id) {
        return Optional.ofNullable(kits.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public void register(Kit kit) {
        kits.put(kit.id().toLowerCase(java.util.Locale.ROOT), kit);
    }

    @Override
    public void unregister(String id) {
        kits.remove(id.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Replaces every registered kit with {@code newKits} - used by {@code /kits reload} and the
     * in-game editor.
     */
    public void reloadFrom(java.util.List<Kit> newKits) {
        kits.clear();
        newKits.forEach(this::register);
    }

    @Override
    public CompletableFuture<KitClaimState> claimState(UUID player, String kitId) {
        return repository.findClaimState(player, kitId);
    }

    @Override
    public CompletableFuture<Long> cooldownRemaining(UUID player, String kitId) {
        Optional<Kit> kit = kit(kitId);
        if (kit.isEmpty() || !kit.get().hasCooldown()) {
            return CompletableFuture.completedFuture(0L);
        }
        return claimState(player, kitId)
                .thenApply(
                        state -> {
                            long readyAt =
                                    state.lastClaimedAt() + kit.get().cooldownSeconds() * 1000L;
                            return Math.max(0, (readyAt - System.currentTimeMillis()) / 1000L);
                        });
    }

    @Override
    public CompletableFuture<ClaimResult> claim(UUID player, String kitId) {
        Optional<Kit> maybeKit = kit(kitId);
        if (maybeKit.isEmpty()) {
            return CompletableFuture.completedFuture(ClaimResult.KIT_NOT_FOUND);
        }
        Kit kit = maybeKit.get();
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return CompletableFuture.completedFuture(ClaimResult.KIT_NOT_FOUND);
        }
        if (kit.permission() != null && !online.hasPermission(kit.permission())) {
            return CompletableFuture.completedFuture(ClaimResult.NO_PERMISSION);
        }
        return claimState(player, kitId)
                .thenCompose(
                        state -> {
                            if (kit.oneTime() && state.claimCount() > 0) {
                                return CompletableFuture.completedFuture(
                                        ClaimResult.ALREADY_CLAIMED_ONE_TIME);
                            }
                            if (kit.maxClaims() != null && state.claimCount() >= kit.maxClaims()) {
                                return CompletableFuture.completedFuture(
                                        ClaimResult.MAX_CLAIMS_REACHED);
                            }
                            if (kit.hasCooldown()) {
                                long readyAt =
                                        state.lastClaimedAt() + kit.cooldownSeconds() * 1000L;
                                if (readyAt > System.currentTimeMillis()) {
                                    return CompletableFuture.completedFuture(
                                            ClaimResult.ON_COOLDOWN);
                                }
                            }
                            int freeSlots = countFreeSlots(online);
                            if (freeSlots < kit.items().size()) {
                                return CompletableFuture.completedFuture(
                                        ClaimResult.INVENTORY_FULL);
                            }
                            return SyncEvents.fire(scheduler, new KitClaimEvent(online, kit))
                                    .thenCompose(
                                            event -> {
                                                if (event.isCancelled()) {
                                                    return CompletableFuture.completedFuture(
                                                            ClaimResult.NO_PERMISSION);
                                                }
                                                for (ItemStack item : kit.items()) {
                                                    online.getInventory().addItem(item.clone());
                                                }
                                                return repository
                                                        .recordClaim(
                                                                player,
                                                                kitId,
                                                                System.currentTimeMillis())
                                                        .thenApply(v -> ClaimResult.SUCCESS);
                                            });
                        });
    }

    private static int countFreeSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        return free;
    }
}

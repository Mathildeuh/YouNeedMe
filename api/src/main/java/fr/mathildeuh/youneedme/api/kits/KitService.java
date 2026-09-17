package fr.mathildeuh.youneedme.api.kits;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Kit registry and per-player claim tracking. */
public interface KitService {

    Collection<Kit> kits();

    Optional<Kit> kit(String id);

    /** Registers or replaces a kit definition - how expansions add a "new kit type" at runtime. */
    void register(Kit kit);

    void unregister(String id);

    CompletableFuture<KitClaimState> claimState(UUID player, String kitId);

    /** Seconds remaining before {@code player} can claim {@code kitId} again, or 0 if ready now. */
    CompletableFuture<Long> cooldownRemaining(UUID player, String kitId);

    /**
     * Attempts to claim a kit: checks permission, one-time/max-claims, cooldown and inventory
     * space, then gives the items and records the claim. The result explains exactly why a claim
     * was refused so commands don't have to re-derive it.
     */
    CompletableFuture<ClaimResult> claim(UUID player, String kitId);

    enum ClaimResult {
        SUCCESS,
        KIT_NOT_FOUND,
        NO_PERMISSION,
        ALREADY_CLAIMED_ONE_TIME,
        MAX_CLAIMS_REACHED,
        ON_COOLDOWN,
        INVENTORY_FULL
    }
}

package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.kits.KitClaimState;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface KitRepository {

    CompletableFuture<KitClaimState> findClaimState(UUID player, String kitId);

    CompletableFuture<Void> recordClaim(UUID player, String kitId, long timestamp);
}

package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.model.PlayerProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileRepository {

    CompletableFuture<PlayerProfile> findOrCreate(UUID uuid, String currentUsername);

    CompletableFuture<Optional<PlayerProfile>> find(UUID uuid);

    CompletableFuture<Optional<UUID>> findUuidByUsername(String username);

    CompletableFuture<Optional<UUID>> findUuidByNickname(String nickname);

    CompletableFuture<Void> save(PlayerProfile profile);
}

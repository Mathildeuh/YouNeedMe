package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.moderation.Punishment;
import fr.mathildeuh.youneedme.api.moderation.PunishmentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentRepository {

    /** Persists a new punishment; the returned record has its generated {@link Punishment#id()}. */
    CompletableFuture<Punishment> save(Punishment punishment);

    CompletableFuture<Void> update(Punishment punishment);

    CompletableFuture<List<Punishment>> findByTarget(UUID target);

    CompletableFuture<List<Punishment>> findByIp(String ip);

    CompletableFuture<Optional<Punishment>> findActive(UUID target, PunishmentType type);

    CompletableFuture<Optional<Punishment>> findActiveIpBan(String ip);

    CompletableFuture<List<Punishment>> findActiveOfType(PunishmentType type, int offset, int limit);
}

package fr.mathildeuh.youneedme.modules.moderation;

import fr.mathildeuh.youneedme.api.event.PunishmentIssuedEvent;
import fr.mathildeuh.youneedme.api.event.PunishmentRevokedEvent;
import fr.mathildeuh.youneedme.api.moderation.ModerationService;
import fr.mathildeuh.youneedme.api.moderation.Punishment;
import fr.mathildeuh.youneedme.api.moderation.PunishmentType;
import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.storage.PunishmentRepository;
import fr.mathildeuh.youneedme.util.SyncEvents;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

public final class ModerationServiceImpl implements ModerationService {

    private final PunishmentRepository repository;
    private final SchedulerAdapter scheduler;

    public ModerationServiceImpl(PunishmentRepository repository, SchedulerAdapter scheduler) {
        this.repository = repository;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Punishment> ban(
            UUID target, @Nullable UUID issuedBy, String reason, @Nullable Long durationMillis) {
        return issue(target, null, PunishmentType.BAN, issuedBy, reason, durationMillis, true);
    }

    @Override
    public CompletableFuture<Punishment> banIp(
            String ip, @Nullable UUID issuedBy, String reason, @Nullable Long durationMillis) {
        return issue(null, ip, PunishmentType.IP_BAN, issuedBy, reason, durationMillis, true);
    }

    @Override
    public CompletableFuture<Punishment> mute(
            UUID target, @Nullable UUID issuedBy, String reason, @Nullable Long durationMillis) {
        return issue(target, null, PunishmentType.MUTE, issuedBy, reason, durationMillis, true);
    }

    @Override
    public CompletableFuture<Punishment> warn(UUID target, @Nullable UUID issuedBy, String reason) {
        return issue(target, null, PunishmentType.WARN, issuedBy, reason, null, false);
    }

    @Override
    public CompletableFuture<Punishment> kick(UUID target, @Nullable UUID issuedBy, String reason) {
        return issue(target, null, PunishmentType.KICK, issuedBy, reason, null, false);
    }

    private CompletableFuture<Punishment> issue(
            @Nullable UUID target,
            @Nullable String ip,
            PunishmentType type,
            @Nullable UUID issuedBy,
            String reason,
            @Nullable Long durationMillis,
            boolean active) {
        Long expiresAt =
                durationMillis == null ? null : System.currentTimeMillis() + durationMillis;
        Punishment draft =
                new Punishment(
                        0,
                        target,
                        ip,
                        type,
                        reason,
                        issuedBy,
                        System.currentTimeMillis(),
                        expiresAt,
                        active,
                        null,
                        null);
        return SyncEvents.fire(scheduler, new PunishmentIssuedEvent(draft))
                .thenCompose(
                        event -> {
                            if (event.isCancelled()) {
                                return CompletableFuture.failedFuture(
                                        new IllegalStateException("Cancelled by another plugin"));
                            }
                            return repository.save(event.getPunishment());
                        });
    }

    @Override
    public CompletableFuture<Boolean> unban(UUID target, @Nullable UUID revokedBy) {
        return revoke(() -> repository.findActive(target, PunishmentType.BAN));
    }

    @Override
    public CompletableFuture<Boolean> unbanIp(String ip, @Nullable UUID revokedBy) {
        return revoke(() -> repository.findActiveIpBan(ip));
    }

    @Override
    public CompletableFuture<Boolean> unmute(UUID target, @Nullable UUID revokedBy) {
        return revoke(() -> repository.findActive(target, PunishmentType.MUTE));
    }

    private CompletableFuture<Boolean> revoke(
            java.util.function.Supplier<CompletableFuture<java.util.Optional<Punishment>>> lookup) {
        return lookup.get()
                .thenCompose(
                        opt -> {
                            if (opt.isEmpty()) {
                                return CompletableFuture.completedFuture(false);
                            }
                            Punishment revoked = opt.get().revoked(null);
                            return repository
                                    .update(revoked)
                                    .thenApply(
                                            v -> {
                                                Bukkit.getPluginManager()
                                                        .callEvent(
                                                                new PunishmentRevokedEvent(
                                                                        revoked));
                                                return true;
                                            });
                        });
    }

    @Override
    public CompletableFuture<Boolean> isBanned(UUID target) {
        return activeBan(target).thenApply(java.util.Objects::nonNull);
    }

    @Override
    public CompletableFuture<Boolean> isIpBanned(String ip) {
        return repository.findActiveIpBan(ip).thenApply(java.util.Optional::isPresent);
    }

    @Override
    public CompletableFuture<Boolean> isMuted(UUID target) {
        return activeMute(target).thenApply(java.util.Objects::nonNull);
    }

    @Override
    public CompletableFuture<@Nullable Punishment> activeBan(UUID target) {
        return repository.findActive(target, PunishmentType.BAN).thenApply(opt -> opt.orElse(null));
    }

    @Override
    public CompletableFuture<@Nullable Punishment> activeMute(UUID target) {
        return repository
                .findActive(target, PunishmentType.MUTE)
                .thenApply(opt -> opt.orElse(null));
    }

    @Override
    public CompletableFuture<@Nullable Punishment> activeIpBan(String ip) {
        return repository.findActiveIpBan(ip).thenApply(opt -> opt.orElse(null));
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID target) {
        return repository.findByTarget(target);
    }

    @Override
    public CompletableFuture<List<Punishment>> activePunishments(
            PunishmentType type, int page, int pageSize) {
        return repository.findActiveOfType(type, Math.max(0, page - 1) * pageSize, pageSize);
    }
}

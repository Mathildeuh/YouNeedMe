package fr.mathildeuh.youneedme.api.moderation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Bans, mutes, kicks and their shared history. Every mutating method fires the matching
 * cancellable event (see {@code fr.mathildeuh.youneedme.api.event}) synchronously on the calling
 * thread before doing any I/O, so listeners can veto a punishment before it is persisted.
 */
public interface ModerationService {

    CompletableFuture<Punishment> ban(
            UUID target, @Nullable UUID issuedBy, String reason, @Nullable Long durationMillis);

    CompletableFuture<Punishment> banIp(
            String ip, @Nullable UUID issuedBy, String reason, @Nullable Long durationMillis);

    CompletableFuture<Punishment> mute(
            UUID target, @Nullable UUID issuedBy, String reason, @Nullable Long durationMillis);

    CompletableFuture<Punishment> warn(UUID target, @Nullable UUID issuedBy, String reason);

    /** Kicks are instantaneous (no active state) but are still logged for {@code /checkpunish}. */
    CompletableFuture<Punishment> kick(UUID target, @Nullable UUID issuedBy, String reason);

    CompletableFuture<Boolean> unban(UUID target, @Nullable UUID revokedBy);

    CompletableFuture<Boolean> unbanIp(String ip, @Nullable UUID revokedBy);

    CompletableFuture<Boolean> unmute(UUID target, @Nullable UUID revokedBy);

    CompletableFuture<Boolean> isBanned(UUID target);

    CompletableFuture<Boolean> isIpBanned(String ip);

    CompletableFuture<Boolean> isMuted(UUID target);

    CompletableFuture<@Nullable Punishment> activeBan(UUID target);

    CompletableFuture<@Nullable Punishment> activeMute(UUID target);

    /** Full punishment history for a player, newest first. */
    CompletableFuture<List<Punishment>> history(UUID target);

    /** All currently-active bans/mutes, for {@code /banlist}. */
    CompletableFuture<List<Punishment>> activePunishments(PunishmentType type, int page, int pageSize);
}

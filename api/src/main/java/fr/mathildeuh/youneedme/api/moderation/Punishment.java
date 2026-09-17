package fr.mathildeuh.youneedme.api.moderation;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A single punishment record, active or historical. {@code target} and {@code targetIp} are
 * independent so an IP-ban can outlive knowledge of which account triggered it, and so a
 * name-ban's history stays queryable even for a player who never had an IP recorded.
 */
public record Punishment(
        long id,
        @Nullable UUID target,
        @Nullable String targetIp,
        PunishmentType type,
        String reason,
        @Nullable UUID issuedBy,
        long issuedAt,
        @Nullable Long expiresAt,
        boolean active,
        @Nullable UUID revokedBy,
        @Nullable Long revokedAt) {

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt <= System.currentTimeMillis();
    }

    public Punishment revoked(@Nullable UUID by) {
        return new Punishment(
                id, target, targetIp, type, reason, issuedBy, issuedAt, expiresAt, false, by, System.currentTimeMillis());
    }
}

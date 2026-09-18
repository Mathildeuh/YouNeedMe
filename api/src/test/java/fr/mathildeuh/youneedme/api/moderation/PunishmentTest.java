package fr.mathildeuh.youneedme.api.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentTest {

    @Test
    void aNullExpiryMeansPermanent() {
        Punishment ban = punishment(null);
        assertTrue(ban.isPermanent());
        assertFalse(ban.isExpired());
    }

    @Test
    void aFutureExpiryIsNeitherPermanentNorExpired() {
        Punishment ban = punishment(System.currentTimeMillis() + 60_000);
        assertFalse(ban.isPermanent());
        assertFalse(ban.isExpired());
    }

    @Test
    void aPastExpiryIsExpiredButNotPermanent() {
        Punishment ban = punishment(System.currentTimeMillis() - 1);
        assertFalse(ban.isPermanent());
        assertTrue(ban.isExpired());
    }

    @Test
    void revokingSetsRevokedByAndTimestampButKeepsEverythingElse() {
        Punishment ban = punishment(null);
        UUID revoker = UUID.randomUUID();

        Punishment revoked = ban.revoked(revoker);

        assertFalse(revoked.active());
        assertEquals(revoker, revoked.revokedBy());
        assertTrue(revoked.revokedAt() != null && revoked.revokedAt() > 0);
        assertEquals(ban.target(), revoked.target());
        assertEquals(ban.reason(), revoked.reason());
    }

    private static Punishment punishment(Long expiresAt) {
        return new Punishment(
                1,
                UUID.randomUUID(),
                null,
                PunishmentType.BAN,
                "testing",
                UUID.randomUUID(),
                System.currentTimeMillis(),
                expiresAt,
                true,
                null,
                null);
    }
}

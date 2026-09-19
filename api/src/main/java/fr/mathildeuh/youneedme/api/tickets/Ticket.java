package fr.mathildeuh.youneedme.api.tickets;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** A player support ticket, from creation through an optional staff claim to closure. */
public record Ticket(
        long id,
        UUID player,
        String playerLastKnownUsername,
        @Nullable String category,
        Status status,
        @Nullable UUID claimedBy,
        @Nullable String claimedByUsername,
        long createdAt,
        @Nullable Long closedAt,
        @Nullable UUID closedBy,
        @Nullable String closedByUsername) {

    public enum Status {
        OPEN,
        CLAIMED,
        CLOSED
    }
}

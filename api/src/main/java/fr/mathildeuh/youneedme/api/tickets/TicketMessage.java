package fr.mathildeuh.youneedme.api.tickets;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** One message in a {@link Ticket}'s thread - the opening message is the ticket's first one. */
public record TicketMessage(
        long id,
        long ticketId,
        @Nullable UUID author,
        String authorUsername,
        boolean staffMessage,
        String message,
        long sentAt) {}

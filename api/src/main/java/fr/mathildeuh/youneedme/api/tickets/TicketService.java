package fr.mathildeuh.youneedme.api.tickets;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/** Player support tickets: create, reply, claim, close - and a transcript on closure. */
public interface TicketService {

    /** Configured categories, in order; empty if the module has none set up. */
    List<String> categories();

    CompletableFuture<Ticket> create(
            UUID player, String playerUsername, @Nullable String category, String message);

    CompletableFuture<Optional<Ticket>> find(long id);

    /** Every ticket not yet closed (OPEN or CLAIMED), oldest first - the staff queue. */
    CompletableFuture<List<Ticket>> listOpen();

    /** A player's own not-yet-closed tickets. */
    CompletableFuture<List<Ticket>> openByPlayer(UUID player);

    /** A player's closed tickets, most recent first. */
    CompletableFuture<List<Ticket>> history(UUID player);

    CompletableFuture<List<TicketMessage>> messages(long ticketId);

    CompletableFuture<ClaimResult> claim(long ticketId, UUID staff, String staffUsername);

    CompletableFuture<ReplyResult> reply(
            long ticketId,
            UUID author,
            String authorUsername,
            String message,
            boolean staffMessage);

    /**
     * Closes the ticket and writes its full transcript to {@code plugins/YouNeedMe/tickets/} - the
     * returned result's {@link CloseResult#SUCCESS} carries no transcript path since the write
     * happens after the DB update; callers wanting the path should follow up with {@link
     * #messages}.
     */
    CompletableFuture<CloseResult> close(long ticketId, UUID closedBy, String closedByUsername);

    enum ClaimResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_CLAIMED,
        ALREADY_CLOSED
    }

    enum ReplyResult {
        SUCCESS,
        NOT_FOUND,
        CLOSED
    }

    enum CloseResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_CLOSED
    }
}

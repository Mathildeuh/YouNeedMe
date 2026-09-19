package fr.mathildeuh.youneedme.modules.tickets;

import fr.mathildeuh.youneedme.api.storage.TicketRepository;
import fr.mathildeuh.youneedme.api.tickets.Ticket;
import fr.mathildeuh.youneedme.api.tickets.TicketMessage;
import fr.mathildeuh.youneedme.api.tickets.TicketService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

public final class TicketServiceImpl implements TicketService {

    private final TicketRepository repository;
    private final List<String> categories;
    private final Path transcriptsDir;
    private final Logger logger;

    public TicketServiceImpl(
            TicketRepository repository,
            List<String> categories,
            Path transcriptsDir,
            Logger logger) {
        this.repository = repository;
        this.categories = categories;
        this.transcriptsDir = transcriptsDir;
        this.logger = logger;
    }

    @Override
    public List<String> categories() {
        return categories;
    }

    @Override
    public CompletableFuture<Ticket> create(
            UUID player, String playerUsername, @Nullable String category, String message) {
        long now = System.currentTimeMillis();
        Ticket ticket =
                new Ticket(
                        0,
                        player,
                        playerUsername,
                        category,
                        Ticket.Status.OPEN,
                        null,
                        null,
                        now,
                        null,
                        null,
                        null);
        return repository
                .save(ticket)
                .thenCompose(
                        saved ->
                                repository
                                        .addMessage(
                                                new TicketMessage(
                                                        0,
                                                        saved.id(),
                                                        player,
                                                        playerUsername,
                                                        false,
                                                        message,
                                                        now))
                                        .thenApply(m -> saved));
    }

    @Override
    public CompletableFuture<Optional<Ticket>> find(long id) {
        return repository.findTicket(id);
    }

    @Override
    public CompletableFuture<List<Ticket>> listOpen() {
        return repository.findByStatuses(List.of(Ticket.Status.OPEN, Ticket.Status.CLAIMED));
    }

    @Override
    public CompletableFuture<List<Ticket>> openByPlayer(UUID player) {
        return repository
                .findByPlayer(player)
                .thenApply(
                        list ->
                                list.stream()
                                        .filter(t -> t.status() != Ticket.Status.CLOSED)
                                        .toList());
    }

    @Override
    public CompletableFuture<List<Ticket>> history(UUID player) {
        return repository
                .findByPlayer(player)
                .thenApply(
                        list ->
                                list.stream()
                                        .filter(t -> t.status() == Ticket.Status.CLOSED)
                                        .toList());
    }

    @Override
    public CompletableFuture<List<TicketMessage>> messages(long ticketId) {
        return repository.messages(ticketId);
    }

    @Override
    public CompletableFuture<ClaimResult> claim(long ticketId, UUID staff, String staffUsername) {
        return repository
                .findTicket(ticketId)
                .thenCompose(
                        opt -> {
                            if (opt.isEmpty()) {
                                return CompletableFuture.completedFuture(ClaimResult.NOT_FOUND);
                            }
                            Ticket ticket = opt.get();
                            if (ticket.status() == Ticket.Status.CLOSED) {
                                return CompletableFuture.completedFuture(
                                        ClaimResult.ALREADY_CLOSED);
                            }
                            if (ticket.status() == Ticket.Status.CLAIMED) {
                                return CompletableFuture.completedFuture(
                                        ClaimResult.ALREADY_CLAIMED);
                            }
                            Ticket claimed =
                                    new Ticket(
                                            ticket.id(),
                                            ticket.player(),
                                            ticket.playerLastKnownUsername(),
                                            ticket.category(),
                                            Ticket.Status.CLAIMED,
                                            staff,
                                            staffUsername,
                                            ticket.createdAt(),
                                            ticket.closedAt(),
                                            ticket.closedBy(),
                                            ticket.closedByUsername());
                            return repository.update(claimed).thenApply(v -> ClaimResult.SUCCESS);
                        });
    }

    @Override
    public CompletableFuture<ReplyResult> reply(
            long ticketId,
            UUID author,
            String authorUsername,
            String message,
            boolean staffMessage) {
        return repository
                .findTicket(ticketId)
                .thenCompose(
                        opt -> {
                            if (opt.isEmpty()) {
                                return CompletableFuture.completedFuture(ReplyResult.NOT_FOUND);
                            }
                            if (opt.get().status() == Ticket.Status.CLOSED) {
                                return CompletableFuture.completedFuture(ReplyResult.CLOSED);
                            }
                            return repository
                                    .addMessage(
                                            new TicketMessage(
                                                    0,
                                                    ticketId,
                                                    author,
                                                    authorUsername,
                                                    staffMessage,
                                                    message,
                                                    System.currentTimeMillis()))
                                    .thenApply(m -> ReplyResult.SUCCESS);
                        });
    }

    @Override
    public CompletableFuture<CloseResult> close(
            long ticketId, UUID closedBy, String closedByUsername) {
        return repository
                .findTicket(ticketId)
                .thenCompose(
                        opt -> {
                            if (opt.isEmpty()) {
                                return CompletableFuture.completedFuture(CloseResult.NOT_FOUND);
                            }
                            Ticket ticket = opt.get();
                            if (ticket.status() == Ticket.Status.CLOSED) {
                                return CompletableFuture.completedFuture(
                                        CloseResult.ALREADY_CLOSED);
                            }
                            Ticket closed =
                                    new Ticket(
                                            ticket.id(),
                                            ticket.player(),
                                            ticket.playerLastKnownUsername(),
                                            ticket.category(),
                                            Ticket.Status.CLOSED,
                                            ticket.claimedBy(),
                                            ticket.claimedByUsername(),
                                            ticket.createdAt(),
                                            System.currentTimeMillis(),
                                            closedBy,
                                            closedByUsername);
                            return repository
                                    .update(closed)
                                    .thenCompose(v -> writeTranscript(closed))
                                    .thenApply(v -> CloseResult.SUCCESS);
                        });
    }

    private CompletableFuture<Void> writeTranscript(Ticket ticket) {
        return repository
                .messages(ticket.id())
                .thenAccept(
                        messages -> {
                            try {
                                Files.createDirectories(transcriptsDir);
                                StringBuilder builder = new StringBuilder(96);
                                builder.append("Ticket #")
                                        .append(ticket.id())
                                        .append(" - ")
                                        .append(ticket.playerLastKnownUsername())
                                        .append(
                                                ticket.category() == null
                                                        ? ""
                                                        : " [" + ticket.category() + "]")
                                        .append("\nOpened: ")
                                        .append(Instant.ofEpochMilli(ticket.createdAt()))
                                        .append("\nClosed by: ")
                                        .append(ticket.closedByUsername())
                                        .append('\n')
                                        .append("-".repeat(40))
                                        .append('\n');
                                for (TicketMessage message : messages) {
                                    builder.append('[')
                                            .append(Instant.ofEpochMilli(message.sentAt()))
                                            .append("] ")
                                            .append(message.staffMessage() ? "(staff) " : "")
                                            .append(message.authorUsername())
                                            .append(": ")
                                            .append(message.message())
                                            .append('\n');
                                }
                                Path file =
                                        transcriptsDir.resolve("ticket-" + ticket.id() + ".txt");
                                Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                logger.log(
                                        Level.WARNING,
                                        "Could not write transcript for ticket " + ticket.id(),
                                        e);
                            }
                        });
    }
}

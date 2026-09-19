package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.tickets.Ticket;
import fr.mathildeuh.youneedme.api.tickets.TicketMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TicketRepository {

    CompletableFuture<Ticket> save(Ticket ticket);

    CompletableFuture<Void> update(Ticket ticket);

    CompletableFuture<Optional<Ticket>> findTicket(long id);

    CompletableFuture<List<Ticket>> findByStatuses(List<Ticket.Status> statuses);

    CompletableFuture<List<Ticket>> findByPlayer(UUID player);

    CompletableFuture<TicketMessage> addMessage(TicketMessage message);

    CompletableFuture<List<TicketMessage>> messages(long ticketId);
}

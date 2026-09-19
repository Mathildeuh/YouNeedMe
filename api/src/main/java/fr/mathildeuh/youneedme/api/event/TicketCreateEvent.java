package fr.mathildeuh.youneedme.api.event;

import fr.mathildeuh.youneedme.api.tickets.Ticket;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after a support ticket is created and persisted - informational, not cancellable. */
public class TicketCreateEvent extends YnmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Ticket ticket;

    public TicketCreateEvent(Player player, Ticket ticket) {
        super(player);
        this.ticket = ticket;
    }

    public Ticket getTicket() {
        return ticket;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

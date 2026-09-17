package fr.mathildeuh.youneedme.api.event;

import fr.mathildeuh.youneedme.api.moderation.Punishment;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a ban/mute/warn/kick is persisted and enforced. {@code punishment.id()} is not yet
 * assigned at this point (it is still {@code 0}); cancelling stops the punishment entirely.
 */
public class PunishmentIssuedEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Punishment punishment;
    private boolean cancelled;

    public PunishmentIssuedEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

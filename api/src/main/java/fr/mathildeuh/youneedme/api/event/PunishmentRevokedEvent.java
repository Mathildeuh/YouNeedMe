package fr.mathildeuh.youneedme.api.event;

import fr.mathildeuh.youneedme.api.moderation.Punishment;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after an unban/unmute is persisted (informational only - not cancellable, it already happened). */
public class PunishmentRevokedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Punishment punishment;

    public PunishmentRevokedEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

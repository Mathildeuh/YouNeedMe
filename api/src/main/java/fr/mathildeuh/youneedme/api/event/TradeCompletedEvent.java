package fr.mathildeuh.youneedme.api.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after a {@code /trade} between two players completes successfully. */
public class TradeCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerA;
    private final UUID playerB;

    public TradeCompletedEvent(UUID playerA, UUID playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

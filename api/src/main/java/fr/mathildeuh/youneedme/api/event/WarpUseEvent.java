package fr.mathildeuh.youneedme.api.event;

import fr.mathildeuh.youneedme.api.model.Warp;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired before a player teleports to a warp (after cost/permission checks pass). */
public class WarpUseEvent extends YnmPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Warp warp;
    private boolean cancelled;

    public WarpUseEvent(Player player, Warp warp) {
        super(player);
        this.warp = warp;
    }

    public Warp getWarp() {
        return warp;
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

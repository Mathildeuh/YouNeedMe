package fr.mathildeuh.youneedme.api.event;

import fr.mathildeuh.youneedme.api.model.Position;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired before a home is created or moved. Cancelling leaves any existing home untouched. */
public class HomeSetEvent extends YnmPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String homeName;
    private final Position position;
    private final boolean overwrite;
    private boolean cancelled;

    public HomeSetEvent(Player player, String homeName, Position position, boolean overwrite) {
        super(player);
        this.homeName = homeName;
        this.position = position;
        this.overwrite = overwrite;
    }

    public String getHomeName() {
        return homeName;
    }

    public Position getPosition() {
        return position;
    }

    /** {@code true} if this call replaces an existing home of the same name rather than creating a new one. */
    public boolean isOverwrite() {
        return overwrite;
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

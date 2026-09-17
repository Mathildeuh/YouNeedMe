package fr.mathildeuh.youneedme.api.event;

import fr.mathildeuh.youneedme.api.kits.Kit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after eligibility checks (permission/cooldown/max-claims) pass, before items are given. */
public class KitClaimEvent extends YnmPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Kit kit;
    private boolean cancelled;

    public KitClaimEvent(Player player, Kit kit) {
        super(player);
        this.kit = kit;
    }

    public Kit getKit() {
        return kit;
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

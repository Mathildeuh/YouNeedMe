package fr.mathildeuh.youneedme.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Fired before an item is listed on the auction house. */
public class AuctionListEvent extends YnmPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack item;
    private double price;
    private boolean cancelled;

    public AuctionListEvent(Player player, ItemStack item, double price) {
        super(player);
        this.item = item;
        this.price = price;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getPrice() {
        return price;
    }

    /** Listeners may adjust the final listing price (e.g. to apply a tax or a discount permission). */
    public void setPrice(double price) {
        this.price = price;
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

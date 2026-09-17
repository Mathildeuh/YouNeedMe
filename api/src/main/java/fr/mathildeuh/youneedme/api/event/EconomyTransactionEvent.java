package fr.mathildeuh.youneedme.api.event;

import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before any balance change is persisted - deposit, withdraw, set, or one leg of a
 * {@code /pay} transfer (which fires twice: once per account). The account involved may be
 * offline, so this carries a {@link UUID} rather than a {@link org.bukkit.entity.Player}.
 */
public class EconomyTransactionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Type {
        DEPOSIT,
        WITHDRAW,
        SET_BALANCE
    }

    private final UUID account;
    private final String currencyId;
    private final Type type;
    private final double amount;
    private final String reason;
    private final @Nullable UUID relatedAccount;
    private boolean cancelled;

    public EconomyTransactionEvent(
            UUID account, String currencyId, Type type, double amount, String reason, @Nullable UUID relatedAccount) {
        this.account = account;
        this.currencyId = currencyId;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.relatedAccount = relatedAccount;
    }

    public UUID getAccount() {
        return account;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public Type getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    /** Machine-readable cause, e.g. {@code "pay"}, {@code "shop-buy"}, {@code "eco-admin"}. */
    public String getReason() {
        return reason;
    }

    /** The other party for a transfer (e.g. the {@code /pay} recipient), if any. */
    public @Nullable UUID getRelatedAccount() {
        return relatedAccount;
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

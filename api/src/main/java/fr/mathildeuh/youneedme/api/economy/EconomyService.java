package fr.mathildeuh.youneedme.api.economy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * YouNeedMe's internal economy. This is also exposed to the rest of the server through a {@code
 * net.milkbowl.vault.economy.Economy} provider (see {@code core}'s Vault integration), so any other
 * plugin can transact against it without knowing YouNeedMe exists - use this interface directly
 * only when you need YouNeedMe-specific features Vault's API doesn't have, such as multi-currency
 * support or {@link #transferAtomic}.
 *
 * <p>Every mutating call is serialized per-player internally (a spam of {@code /pay} cannot cause a
 * double-spend), and every method is safe to call from any thread.
 */
public interface EconomyService {

    String DEFAULT_CURRENCY = "default";

    List<Currency> currencies();

    Currency currency(String currencyId);

    CompletableFuture<Double> balance(UUID player, String currencyId);

    default CompletableFuture<Double> balance(UUID player) {
        return balance(player, DEFAULT_CURRENCY);
    }

    CompletableFuture<Boolean> has(UUID player, String currencyId, double amount);

    CompletableFuture<EconomyResult> deposit(UUID player, String currencyId, double amount);

    CompletableFuture<EconomyResult> withdraw(UUID player, String currencyId, double amount);

    CompletableFuture<EconomyResult> setBalance(UUID player, String currencyId, double amount);

    default CompletableFuture<EconomyResult> deposit(UUID player, double amount) {
        return deposit(player, DEFAULT_CURRENCY, amount);
    }

    default CompletableFuture<EconomyResult> withdraw(UUID player, double amount) {
        return withdraw(player, DEFAULT_CURRENCY, amount);
    }

    /**
     * Withdraws from {@code from} and deposits to {@code to} as a single atomic unit: on
     * insufficient funds neither balance changes. This is what {@code /pay} uses, and is the right
     * primitive for any plugin-to-plugin transfer.
     */
    CompletableFuture<EconomyResult> transferAtomic(
            UUID from, UUID to, String currencyId, double amount);

    default CompletableFuture<EconomyResult> transferAtomic(UUID from, UUID to, double amount) {
        return transferAtomic(from, to, DEFAULT_CURRENCY, amount);
    }

    /** Top balances for {@code /baltop}, richest first. */
    CompletableFuture<List<BalanceEntry>> top(String currencyId, int page, int pageSize);

    record BalanceEntry(UUID player, String lastKnownUsername, double balance) {}
}

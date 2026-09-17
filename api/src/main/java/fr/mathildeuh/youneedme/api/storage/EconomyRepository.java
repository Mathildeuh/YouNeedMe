package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.economy.EconomyService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyRepository {

    CompletableFuture<Double> getBalance(UUID player, String currencyId, double defaultBalance);

    /**
     * Atomically applies {@code delta} to the player's balance and returns the resulting balance.
     * Backends must serialize this per-{@code (player, currencyId)} pair (row-level lock / CAS loop
     * / single-writer actor - the implementation's choice) so concurrent deposits/withdrawals for
     * the same player can never race.
     */
    CompletableFuture<Double> applyDelta(
            UUID player, String currencyId, double delta, double defaultBalance);

    CompletableFuture<Void> setBalance(UUID player, String currencyId, double amount);

    CompletableFuture<List<EconomyService.BalanceEntry>> top(
            String currencyId, int offset, int limit);

    CompletableFuture<Void> logTransaction(EconomyTransactionLog log);

    CompletableFuture<List<EconomyTransactionLog>> history(UUID player, int limit);
}

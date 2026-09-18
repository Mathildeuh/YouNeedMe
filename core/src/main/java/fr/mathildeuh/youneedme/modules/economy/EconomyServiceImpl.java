package fr.mathildeuh.youneedme.modules.economy;

import fr.mathildeuh.youneedme.api.economy.Currency;
import fr.mathildeuh.youneedme.api.economy.EconomyResult;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.event.EconomyTransactionEvent;
import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.storage.EconomyRepository;
import fr.mathildeuh.youneedme.api.storage.EconomyTransactionLog;
import fr.mathildeuh.youneedme.util.KeyedMutex;
import fr.mathildeuh.youneedme.util.SyncEvents;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Per-player transactions are serialized through {@link KeyedMutex} so a spammed {@code /pay} or
 * concurrent shop purchases can never race into a double-spend; the actual persisted increment is
 * additionally atomic at the storage layer (see each {@code DataStorage} backend).
 */
public final class EconomyServiceImpl implements EconomyService {

    private final EconomyRepository repository;
    private final SchedulerAdapter scheduler;
    private final List<Currency> currencies;
    private final double defaultBalance;
    private final double minBalance;
    private final Double maxBalance;
    private final KeyedMutex<UUID> mutex = new KeyedMutex<>();

    public EconomyServiceImpl(
            EconomyRepository repository, SchedulerAdapter scheduler, ConfigurationSection config) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.defaultBalance = config.getDouble("starting-balance", 100.0);
        this.minBalance = config.getDouble("minimum-balance", 0.0);
        double configuredMax = config.getDouble("maximum-balance", -1);
        this.maxBalance = configuredMax > 0 ? configuredMax : null;
        this.currencies =
                List.of(
                        new Currency(
                                DEFAULT_CURRENCY,
                                config.getString("currency.symbol", "$"),
                                config.getString("currency.singular", "dollar"),
                                config.getString("currency.plural", "dollars"),
                                config.getInt("currency.decimal-places", 2)));
    }

    @Override
    public List<Currency> currencies() {
        return currencies;
    }

    @Override
    public Currency currency(String currencyId) {
        return currencies.stream()
                .filter(c -> c.id().equals(currencyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown currency: " + currencyId));
    }

    @Override
    public CompletableFuture<Double> balance(UUID player, String currencyId) {
        return repository.getBalance(player, currencyId, defaultBalance);
    }

    @Override
    public CompletableFuture<Boolean> has(UUID player, String currencyId, double amount) {
        return balance(player, currencyId).thenApply(b -> b >= amount);
    }

    /**
     * Materializes a {@code default}-currency balance row for {@code player} if one doesn't exist
     * yet, so a player who has never deposited/withdrawn still shows up in {@code /baltop} - which
     * reads directly from the balances table, unlike {@link #balance} which only returns a virtual
     * default without persisting it. Called on join; a no-op for returning players.
     */
    public void ensureAccountExists(UUID player) {
        repository.applyDelta(player, DEFAULT_CURRENCY, 0, defaultBalance);
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(UUID player, String currencyId, double amount) {
        if (amount < 0) {
            return CompletableFuture.completedFuture(
                    EconomyResult.failure(
                            EconomyResult.Status.ERROR, 0, "Amount must not be negative"));
        }
        return mutex.runExclusive(
                player, () -> applyChecked(player, currencyId, amount, "deposit", null));
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(
            UUID player, String currencyId, double amount) {
        if (amount < 0) {
            return CompletableFuture.completedFuture(
                    EconomyResult.failure(
                            EconomyResult.Status.ERROR, 0, "Amount must not be negative"));
        }
        return mutex.runExclusive(
                player, () -> applyChecked(player, currencyId, -amount, "withdraw", null));
    }

    @Override
    public CompletableFuture<EconomyResult> setBalance(
            UUID player, String currencyId, double amount) {
        return mutex.runExclusive(
                player,
                () -> {
                    if (maxBalance != null && amount > maxBalance) {
                        return CompletableFuture.completedFuture(
                                EconomyResult.failure(
                                        EconomyResult.Status.ABOVE_MAXIMUM,
                                        amount,
                                        "Above maximum balance"));
                    }
                    EconomyTransactionEvent event =
                            new EconomyTransactionEvent(
                                    player,
                                    currencyId,
                                    EconomyTransactionEvent.Type.SET_BALANCE,
                                    amount,
                                    "eco-set",
                                    null);
                    return SyncEvents.fire(scheduler, event)
                            .thenCompose(
                                    fired -> {
                                        if (fired.isCancelled()) {
                                            return balance(player, currencyId)
                                                    .thenApply(
                                                            b ->
                                                                    EconomyResult.failure(
                                                                            EconomyResult.Status
                                                                                    .ERROR,
                                                                            b,
                                                                            "Cancelled by another"
                                                                                    + " plugin"));
                                        }
                                        return repository
                                                .setBalance(player, currencyId, amount)
                                                .thenCompose(
                                                        v ->
                                                                logAndReturn(
                                                                        player,
                                                                        currencyId,
                                                                        amount,
                                                                        amount,
                                                                        "eco-set",
                                                                        null))
                                                .thenApply(v -> EconomyResult.success(amount));
                                    });
                });
    }

    @Override
    public CompletableFuture<EconomyResult> transferAtomic(
            UUID from, UUID to, String currencyId, double amount) {
        if (from.equals(to)) {
            return CompletableFuture.completedFuture(
                    EconomyResult.failure(
                            EconomyResult.Status.ERROR, 0, "Cannot transfer to self"));
        }
        if (amount <= 0) {
            return CompletableFuture.completedFuture(
                    EconomyResult.failure(
                            EconomyResult.Status.ERROR, 0, "Amount must be positive"));
        }
        UUID lockKey = from.compareTo(to) < 0 ? from : to;
        UUID secondary = from.compareTo(to) < 0 ? to : from;
        return mutex.runExclusive(
                lockKey,
                () ->
                        mutex.runExclusive(
                                secondary,
                                () ->
                                        balance(from, currencyId)
                                                .thenCompose(
                                                        fromBalance -> {
                                                            if (fromBalance < amount) {
                                                                return CompletableFuture
                                                                        .completedFuture(
                                                                                EconomyResult
                                                                                        .failure(
                                                                                                EconomyResult
                                                                                                        .Status
                                                                                                        .INSUFFICIENT_FUNDS,
                                                                                                fromBalance,
                                                                                                "Insufficient"
                                                                                                    + " funds"));
                                                            }
                                                            return applyChecked(
                                                                            from,
                                                                            currencyId,
                                                                            -amount,
                                                                            "pay",
                                                                            to)
                                                                    .thenCompose(
                                                                            withdrawResult -> {
                                                                                if (!withdrawResult
                                                                                        .isSuccess()) {
                                                                                    return CompletableFuture
                                                                                            .completedFuture(
                                                                                                    withdrawResult);
                                                                                }
                                                                                return applyChecked(
                                                                                        to,
                                                                                        currencyId,
                                                                                        amount,
                                                                                        "pay",
                                                                                        from);
                                                                            });
                                                        })));
    }

    @Override
    public CompletableFuture<List<BalanceEntry>> top(String currencyId, int page, int pageSize) {
        return repository.top(currencyId, Math.max(0, page - 1) * pageSize, pageSize);
    }

    private CompletableFuture<EconomyResult> applyChecked(
            UUID player, String currencyId, double delta, String reason, UUID relatedPlayer) {
        EconomyTransactionEvent.Type type =
                delta >= 0
                        ? EconomyTransactionEvent.Type.DEPOSIT
                        : EconomyTransactionEvent.Type.WITHDRAW;
        EconomyTransactionEvent event =
                new EconomyTransactionEvent(
                        player, currencyId, type, Math.abs(delta), reason, relatedPlayer);
        return SyncEvents.fire(scheduler, event)
                .thenCompose(
                        fired -> {
                            if (fired.isCancelled()) {
                                return balance(player, currencyId)
                                        .thenApply(
                                                b ->
                                                        EconomyResult.failure(
                                                                EconomyResult.Status.ERROR,
                                                                b,
                                                                "Cancelled by another plugin"));
                            }
                            return balance(player, currencyId)
                                    .thenCompose(
                                            current -> {
                                                double projected = current + delta;
                                                if (delta < 0 && projected < minBalance) {
                                                    return CompletableFuture.completedFuture(
                                                            EconomyResult.failure(
                                                                    EconomyResult.Status
                                                                            .INSUFFICIENT_FUNDS,
                                                                    current,
                                                                    "Insufficient funds"));
                                                }
                                                if (maxBalance != null && projected > maxBalance) {
                                                    return CompletableFuture.completedFuture(
                                                            EconomyResult.failure(
                                                                    EconomyResult.Status
                                                                            .ABOVE_MAXIMUM,
                                                                    current,
                                                                    "Above maximum balance"));
                                                }
                                                return repository
                                                        .applyDelta(
                                                                player,
                                                                currencyId,
                                                                delta,
                                                                defaultBalance)
                                                        .thenCompose(
                                                                newBalance ->
                                                                        logAndReturn(
                                                                                player,
                                                                                currencyId,
                                                                                delta,
                                                                                newBalance,
                                                                                reason,
                                                                                relatedPlayer))
                                                        .thenApply(EconomyResult::success);
                                            });
                        });
    }

    private CompletableFuture<Double> logAndReturn(
            UUID player,
            String currencyId,
            double delta,
            double balanceAfter,
            String reason,
            UUID relatedPlayer) {
        EconomyTransactionLog log =
                new EconomyTransactionLog(
                        0,
                        player,
                        currencyId,
                        delta,
                        balanceAfter,
                        reason,
                        relatedPlayer,
                        System.currentTimeMillis());
        return repository.logTransaction(log).thenApply(v -> balanceAfter);
    }
}

package fr.mathildeuh.youneedme.api.storage;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** One audit entry in a player's economy history (deposit, withdraw, pay, purchase, ...). */
public record EconomyTransactionLog(
        long id,
        UUID player,
        String currencyId,
        double delta,
        double balanceAfter,
        String reason,
        @Nullable UUID relatedPlayer,
        long timestamp) {}

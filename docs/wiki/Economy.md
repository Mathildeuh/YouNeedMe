# Economy

YouNeedMe's internal economy is single-currency by default (extensible to more) and is exposed as
a native **Vault** `Economy` provider automatically when Vault is installed — any other plugin
(shops, quests, ranks) can transact against it without knowing YouNeedMe exists.

Every mutating operation is serialized per-player (`KeyedMutex`), so spamming `/pay` cannot cause a
double-spend, and every balance change fires a cancellable `EconomyTransactionEvent` before it's
persisted.

## Commands

| Command | Description |
| --- | --- |
| `/balance [player]` (`/bal`) | Check your (or another player's) balance |
| `/baltop [page]` | Top balances, richest first |
| `/pay <player> <amount>` | Transfer money atomically to another player |
| `/eco <give\|take\|set\|reset> <player> <amount>` | Admin balance management |

## Configuration (`modules/economy.yml`)

```yaml
starting-balance: 100.0
minimum-balance: 0.0
maximum-balance: -1     # -1 = unlimited
currency:
  symbol: "$"
  singular: dollar
  plural: dollars
  decimal-places: 2
```

## For developers

```java
YouNeedMeAPI.economy().ifPresent(economy ->
        economy.transferAtomic(fromUuid, toUuid, 50.0)
                .thenAccept(result -> { /* ... */ }));
```

See [API](API) for the full `EconomyService` contract and the `EconomyTransactionEvent`.

package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.storage.EconomyRepository;
import fr.mathildeuh.youneedme.api.storage.EconomyTransactionLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlEconomyRepository implements EconomyRepository {

    private final SqlExecutor sql;
    private final SqlDialect dialect;

    public SqlEconomyRepository(SqlExecutor sql, SqlDialect dialect) {
        this.sql = sql;
        this.dialect = dialect;
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID player, String currencyId, double defaultBalance) {
        return sql.submit(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT balance FROM ynm_balances WHERE player = ? AND currency_id = ?")) {
                ps.setString(1, player.toString());
                ps.setString(2, currencyId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : defaultBalance;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Double> applyDelta(UUID player, String currencyId, double delta, double defaultBalance) {
        return sql.submit(connection -> {
            ensureRowExists(connection, player, currencyId, defaultBalance);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ynm_balances SET balance = balance + ? WHERE player = ? AND currency_id = ?")) {
                update.setDouble(1, delta);
                update.setString(2, player.toString());
                update.setString(3, currencyId);
                update.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT balance FROM ynm_balances WHERE player = ? AND currency_id = ?")) {
                select.setString(1, player.toString());
                select.setString(2, currencyId);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    return rs.getDouble(1);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> setBalance(UUID player, String currencyId, double amount) {
        String upsert = dialect.upsert(
                "ynm_balances",
                new String[] {"player", "currency_id", "balance"},
                new String[] {"player", "currency_id"},
                new String[] {"balance"});
        return sql.run(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                ps.setString(1, player.toString());
                ps.setString(2, currencyId);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<List<EconomyService.BalanceEntry>> top(String currencyId, int offset, int limit) {
        return sql.submit(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    SELECT b.player, p.last_username, b.balance
                    FROM ynm_balances b
                    JOIN ynm_player_profiles p ON p.uuid = b.player
                    WHERE b.currency_id = ?
                    ORDER BY b.balance DESC
                    LIMIT ? OFFSET ?
                    """)) {
                ps.setString(1, currencyId);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<EconomyService.BalanceEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        entries.add(new EconomyService.BalanceEntry(
                                UUID.fromString(rs.getString(1)), rs.getString(2), rs.getDouble(3)));
                    }
                    return entries;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> logTransaction(EconomyTransactionLog log) {
        return sql.run(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO ynm_economy_log (player, currency_id, delta, balance_after, reason, related_player, ts)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, log.player().toString());
                ps.setString(2, log.currencyId());
                ps.setDouble(3, log.delta());
                ps.setDouble(4, log.balanceAfter());
                ps.setString(5, log.reason());
                ps.setString(6, log.relatedPlayer() == null ? null : log.relatedPlayer().toString());
                ps.setLong(7, log.timestamp());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<List<EconomyTransactionLog>> history(UUID player, int limit) {
        return sql.submit(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM ynm_economy_log WHERE player = ? ORDER BY ts DESC LIMIT ?")) {
                ps.setString(1, player.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<EconomyTransactionLog> logs = new ArrayList<>();
                    while (rs.next()) {
                        String related = rs.getString("related_player");
                        logs.add(new EconomyTransactionLog(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("player")),
                                rs.getString("currency_id"),
                                rs.getDouble("delta"),
                                rs.getDouble("balance_after"),
                                rs.getString("reason"),
                                related == null ? null : UUID.fromString(related),
                                rs.getLong("ts")));
                    }
                    return logs;
                }
            }
        });
    }

    /** Inserts the {@code (player, currencyId)} row with {@code defaultBalance} iff it doesn't exist yet - a no-op otherwise. */
    private void ensureRowExists(Connection connection, UUID player, String currencyId, double defaultBalance)
            throws SQLException {
        String sqlText = dialect.isMySqlFamily()
                ? "INSERT INTO ynm_balances (player, currency_id, balance) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE player = player"
                : "INSERT INTO ynm_balances (player, currency_id, balance) VALUES (?, ?, ?) "
                        + "ON CONFLICT (player, currency_id) DO NOTHING";
        try (PreparedStatement ps = connection.prepareStatement(sqlText)) {
            ps.setString(1, player.toString());
            ps.setString(2, currencyId);
            ps.setDouble(3, defaultBalance);
            ps.executeUpdate();
        }
    }
}

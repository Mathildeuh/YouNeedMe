package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.kits.KitClaimState;
import fr.mathildeuh.youneedme.api.storage.KitRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlKitRepository implements KitRepository {

    private final SqlExecutor sql;
    private final SqlDialect dialect;

    public SqlKitRepository(SqlExecutor sql, SqlDialect dialect) {
        this.sql = sql;
        this.dialect = dialect;
    }

    @Override
    public CompletableFuture<KitClaimState> findClaimState(UUID player, String kitId) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT claim_count, last_claimed_at FROM ynm_kit_claims WHERE"
                                            + " player = ? AND kit_id = ?")) {
                        ps.setString(1, player.toString());
                        ps.setString(2, kitId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                return new KitClaimState(kitId, 0, 0);
                            }
                            return new KitClaimState(kitId, rs.getLong(1), rs.getLong(2));
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Void> recordClaim(UUID player, String kitId, long timestamp) {
        String sqlText =
                dialect.isMySqlFamily()
                        ? """
                        INSERT INTO ynm_kit_claims (player, kit_id, claim_count, last_claimed_at) VALUES (?, ?, 1, ?)
                        ON DUPLICATE KEY UPDATE claim_count = claim_count + 1, last_claimed_at = ?
                        """
                        : """
                        INSERT INTO ynm_kit_claims (player, kit_id, claim_count, last_claimed_at) VALUES (?, ?, 1, ?)
                        ON CONFLICT (player, kit_id) DO UPDATE
                            SET claim_count = ynm_kit_claims.claim_count + 1, last_claimed_at = excluded.last_claimed_at
                        """;
        return sql.run(
                connection -> {
                    try (PreparedStatement ps = connection.prepareStatement(sqlText)) {
                        ps.setString(1, player.toString());
                        ps.setString(2, kitId);
                        ps.setLong(3, timestamp);
                        if (dialect.isMySqlFamily()) {
                            ps.setLong(4, timestamp);
                        }
                        ps.executeUpdate();
                    }
                });
    }
}

package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.moderation.Punishment;
import fr.mathildeuh.youneedme.api.moderation.PunishmentType;
import fr.mathildeuh.youneedme.api.storage.PunishmentRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlPunishmentRepository implements PunishmentRepository {

    private final SqlExecutor sql;

    public SqlPunishmentRepository(SqlExecutor sql) {
        this.sql = sql;
    }

    @Override
    public CompletableFuture<Punishment> save(Punishment punishment) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO ynm_punishments
                                        (target, target_ip, type, reason, issued_by, issued_at, expires_at, active, revoked_by, revoked_at)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                                    Statement.RETURN_GENERATED_KEYS)) {
                        bindWithoutId(ps, punishment);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            long id = keys.getLong(1);
                            return new Punishment(
                                    id,
                                    punishment.target(),
                                    punishment.targetIp(),
                                    punishment.type(),
                                    punishment.reason(),
                                    punishment.issuedBy(),
                                    punishment.issuedAt(),
                                    punishment.expiresAt(),
                                    punishment.active(),
                                    punishment.revokedBy(),
                                    punishment.revokedAt());
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Void> update(Punishment punishment) {
        return sql.run(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    UPDATE ynm_punishments SET target = ?, target_ip = ?, type = ?, reason = ?, issued_by = ?,
                                        issued_at = ?, expires_at = ?, active = ?, revoked_by = ?, revoked_at = ? WHERE id = ?
                                    """)) {
                        int i = bindWithoutId(ps, punishment);
                        ps.setLong(i, punishment.id());
                        ps.executeUpdate();
                    }
                });
    }

    @Override
    public CompletableFuture<List<Punishment>> findByTarget(UUID target) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_punishments WHERE target = ? ORDER BY"
                                            + " issued_at DESC")) {
                        ps.setString(1, target.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<Punishment>> findByIp(String ip) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_punishments WHERE target_ip = ? ORDER BY"
                                            + " issued_at DESC")) {
                        ps.setString(1, ip);
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActive(UUID target, PunishmentType type) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    SELECT * FROM ynm_punishments
                                    WHERE target = ? AND type = ? AND active = %s
                                    ORDER BY issued_at DESC LIMIT 1
                                    """
                                            .formatted(trueLiteral(connection)))) {
                        ps.setString(1, target.toString());
                        ps.setString(2, type.name());
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next()
                                    ? Optional.of(mapRow(rs))
                                    : Optional.<Punishment>empty();
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveIpBan(String ip) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    SELECT * FROM ynm_punishments
                                    WHERE target_ip = ? AND type = 'IP_BAN' AND active = %s
                                    ORDER BY issued_at DESC LIMIT 1
                                    """
                                            .formatted(trueLiteral(connection)))) {
                        ps.setString(1, ip);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next()
                                    ? Optional.of(mapRow(rs))
                                    : Optional.<Punishment>empty();
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<Punishment>> findActiveOfType(
            PunishmentType type, int offset, int limit) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    SELECT * FROM ynm_punishments WHERE type = ? AND active = %s
                                    ORDER BY issued_at DESC LIMIT ? OFFSET ?
                                    """
                                            .formatted(trueLiteral(connection)))) {
                        ps.setString(1, type.name());
                        ps.setInt(2, limit);
                        ps.setInt(3, offset);
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    /**
     * SQLite has no real boolean literal keyword in all drivers; {@code 1}/{@code TRUE} both parse
     * fine everywhere else.
     */
    private static String trueLiteral(java.sql.Connection connection) throws java.sql.SQLException {
        String product =
                connection
                        .getMetaData()
                        .getDatabaseProductName()
                        .toLowerCase(java.util.Locale.ROOT);
        return product.contains("sqlite") ? "1" : "TRUE";
    }

    private static int bindWithoutId(PreparedStatement ps, Punishment punishment)
            throws java.sql.SQLException {
        int i = 1;
        ps.setString(i++, punishment.target() == null ? null : punishment.target().toString());
        ps.setString(i++, punishment.targetIp());
        ps.setString(i++, punishment.type().name());
        ps.setString(i++, punishment.reason());
        ps.setString(i++, punishment.issuedBy() == null ? null : punishment.issuedBy().toString());
        ps.setLong(i++, punishment.issuedAt());
        if (punishment.expiresAt() == null) {
            ps.setNull(i++, java.sql.Types.BIGINT);
        } else {
            ps.setLong(i++, punishment.expiresAt());
        }
        ps.setBoolean(i++, punishment.active());
        ps.setString(
                i++, punishment.revokedBy() == null ? null : punishment.revokedBy().toString());
        if (punishment.revokedAt() == null) {
            ps.setNull(i++, java.sql.Types.BIGINT);
        } else {
            ps.setLong(i++, punishment.revokedAt());
        }
        return i;
    }

    private static List<Punishment> mapAll(ResultSet rs) throws java.sql.SQLException {
        List<Punishment> punishments = new ArrayList<>();
        while (rs.next()) {
            punishments.add(mapRow(rs));
        }
        return punishments;
    }

    private static Punishment mapRow(ResultSet rs) throws java.sql.SQLException {
        String target = rs.getString("target");
        String issuedBy = rs.getString("issued_by");
        String revokedBy = rs.getString("revoked_by");
        return new Punishment(
                rs.getLong("id"),
                target == null ? null : UUID.fromString(target),
                rs.getString("target_ip"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                issuedBy == null ? null : UUID.fromString(issuedBy),
                rs.getLong("issued_at"),
                nullableLong(rs, "expires_at"),
                rs.getBoolean("active"),
                revokedBy == null ? null : UUID.fromString(revokedBy),
                nullableLong(rs, "revoked_at"));
    }

    /**
     * SQLite's driver throws "Bad value for type Long" from {@code getObject(column, Long.class)}
     * on a NULL column instead of just returning null - getLong() + wasNull() is the
     * driver-agnostic-safe way to read a nullable integer column.
     */
    private static java.lang.Long nullableLong(ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}

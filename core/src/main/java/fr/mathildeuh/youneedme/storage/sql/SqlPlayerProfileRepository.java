package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.model.PlayerProfile;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.storage.PlayerProfileRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

public final class SqlPlayerProfileRepository implements PlayerProfileRepository {

    private final SqlExecutor sql;
    private final SqlDialect dialect;

    public SqlPlayerProfileRepository(SqlExecutor sql, SqlDialect dialect) {
        this.sql = sql;
        this.dialect = dialect;
    }

    @Override
    public CompletableFuture<PlayerProfile> findOrCreate(UUID uuid, String currentUsername) {
        return find(uuid).thenCompose(existing -> {
            if (existing.isPresent()) {
                PlayerProfile profile = existing.get();
                if (profile.lastKnownUsername().equals(currentUsername)) {
                    return CompletableFuture.completedFuture(profile);
                }
                PlayerProfile renamed = new PlayerProfile(
                        uuid,
                        currentUsername,
                        profile.nickname(),
                        profile.languageCode(),
                        profile.firstJoinedAt(),
                        profile.lastSeenAt(),
                        profile.playtimeSeconds(),
                        profile.lastLocation(),
                        profile.lastDeathLocation());
                return save(renamed).thenApply(v -> renamed);
            }
            long now = System.currentTimeMillis();
            PlayerProfile created = new PlayerProfile(uuid, currentUsername, null, null, now, now, 0, null, null);
            return save(created).thenApply(v -> created);
        });
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> find(UUID uuid) {
        return sql.submit(connection -> {
            try (PreparedStatement ps =
                    connection.prepareStatement("SELECT * FROM ynm_player_profiles WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.<PlayerProfile>empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuidByUsername(String username) {
        return sql.submit(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM ynm_player_profiles WHERE LOWER(last_username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.<UUID>empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuidByNickname(String nickname) {
        return sql.submit(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM ynm_player_profiles WHERE LOWER(nickname) = LOWER(?)")) {
                ps.setString(1, nickname);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.<UUID>empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        String[] columns = {
            "uuid",
            "last_username",
            "nickname",
            "language_code",
            "first_joined_at",
            "last_seen_at",
            "playtime_seconds",
            "last_world",
            "last_x",
            "last_y",
            "last_z",
            "last_yaw",
            "last_pitch",
            "death_world",
            "death_x",
            "death_y",
            "death_z",
            "death_yaw",
            "death_pitch"
        };
        String[] updateColumns = java.util.Arrays.copyOfRange(columns, 1, columns.length);
        String upsert = dialect.upsert("ynm_player_profiles", columns, new String[] {"uuid"}, updateColumns);

        return sql.run(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                int i = 1;
                ps.setString(i++, profile.uuid().toString());
                ps.setString(i++, profile.lastKnownUsername());
                ps.setString(i++, profile.nickname());
                ps.setString(i++, profile.languageCode());
                ps.setLong(i++, profile.firstJoinedAt());
                ps.setLong(i++, profile.lastSeenAt());
                ps.setLong(i++, profile.playtimeSeconds());
                i = setPosition(ps, i, profile.lastLocation());
                setPosition(ps, i, profile.lastDeathLocation());
                ps.executeUpdate();
            }
        });
    }

    private static int setPosition(PreparedStatement ps, int i, @Nullable Position position) throws java.sql.SQLException {
        if (position == null) {
            ps.setNull(i++, java.sql.Types.VARCHAR);
            ps.setNull(i++, java.sql.Types.DOUBLE);
            ps.setNull(i++, java.sql.Types.DOUBLE);
            ps.setNull(i++, java.sql.Types.DOUBLE);
            ps.setNull(i++, java.sql.Types.REAL);
            ps.setNull(i++, java.sql.Types.REAL);
        } else {
            ps.setString(i++, position.worldName());
            ps.setDouble(i++, position.x());
            ps.setDouble(i++, position.y());
            ps.setDouble(i++, position.z());
            ps.setFloat(i++, position.yaw());
            ps.setFloat(i++, position.pitch());
        }
        return i;
    }

    private static @Nullable Position readPosition(ResultSet rs, String prefix) throws java.sql.SQLException {
        String world = rs.getString(prefix + "_world");
        if (world == null) {
            return null;
        }
        return new Position(
                world,
                rs.getDouble(prefix + "_x"),
                rs.getDouble(prefix + "_y"),
                rs.getDouble(prefix + "_z"),
                rs.getFloat(prefix + "_yaw"),
                rs.getFloat(prefix + "_pitch"));
    }

    private static PlayerProfile mapRow(ResultSet rs) throws java.sql.SQLException {
        return new PlayerProfile(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("last_username"),
                rs.getString("nickname"),
                rs.getString("language_code"),
                rs.getLong("first_joined_at"),
                rs.getLong("last_seen_at"),
                rs.getLong("playtime_seconds"),
                readPosition(rs, "last"),
                readPosition(rs, "death"));
    }
}

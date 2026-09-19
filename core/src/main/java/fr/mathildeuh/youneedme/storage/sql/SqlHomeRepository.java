package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.model.Home;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.storage.HomeRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlHomeRepository implements HomeRepository {

    private static final String[] COLUMNS = {
        "owner", "name", "world", "x", "y", "z", "yaw", "pitch", "created_at", "updated_at"
    };

    private final SqlExecutor sql;
    private final SqlDialect dialect;

    public SqlHomeRepository(SqlExecutor sql, SqlDialect dialect) {
        this.sql = sql;
        this.dialect = dialect;
    }

    @Override
    public CompletableFuture<List<Home>> findByOwner(UUID owner) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_homes WHERE owner = ? ORDER BY name")) {
                        ps.setString(1, owner.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            List<Home> homes = new ArrayList<>();
                            while (rs.next()) {
                                homes.add(mapRow(rs));
                            }
                            return homes;
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Optional<Home>> find(UUID owner, String name) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_homes WHERE owner = ? AND name = ?")) {
                        ps.setString(1, owner.toString());
                        ps.setString(2, name);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Void> save(Home home) {
        String[] updateColumns = {"world", "x", "y", "z", "yaw", "pitch", "updated_at"};
        String upsert =
                dialect.upsert("ynm_homes", COLUMNS, new String[] {"owner", "name"}, updateColumns);
        return sql.run(
                connection -> {
                    try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                        ps.setString(1, home.owner().toString());
                        ps.setString(2, home.name());
                        ps.setString(3, home.position().worldName());
                        ps.setDouble(4, home.position().x());
                        ps.setDouble(5, home.position().y());
                        ps.setDouble(6, home.position().z());
                        ps.setFloat(7, home.position().yaw());
                        ps.setFloat(8, home.position().pitch());
                        ps.setLong(9, home.createdAt());
                        ps.setLong(10, home.updatedAt());
                        ps.executeUpdate();
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID owner, String name) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "DELETE FROM ynm_homes WHERE owner = ? AND name = ?")) {
                        ps.setString(1, owner.toString());
                        ps.setString(2, name);
                        return ps.executeUpdate() > 0;
                    }
                });
    }

    @Override
    public CompletableFuture<Integer> count(UUID owner) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT COUNT(*) FROM ynm_homes WHERE owner = ?")) {
                        ps.setString(1, owner.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            return rs.getInt(1);
                        }
                    }
                });
    }

    private static Home mapRow(ResultSet rs) throws java.sql.SQLException {
        return new Home(
                UUID.fromString(rs.getString("owner")),
                rs.getString("name"),
                new Position(
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }
}

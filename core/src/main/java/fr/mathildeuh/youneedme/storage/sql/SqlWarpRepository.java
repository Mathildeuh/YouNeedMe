package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.model.Warp;
import fr.mathildeuh.youneedme.api.storage.WarpRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlWarpRepository implements WarpRepository {

    private static final String[] COLUMNS = {
        "name",
        "world",
        "x",
        "y",
        "z",
        "yaw",
        "pitch",
        "category",
        "permission",
        "cost",
        "description",
        "hidden",
        "created_by",
        "created_at"
    };

    private final SqlExecutor sql;
    private final SqlDialect dialect;

    public SqlWarpRepository(SqlExecutor sql, SqlDialect dialect) {
        this.sql = sql;
        this.dialect = dialect;
    }

    @Override
    public CompletableFuture<List<Warp>> findAll() {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                                    connection.prepareStatement(
                                            "SELECT * FROM ynm_warps ORDER BY name");
                            ResultSet rs = ps.executeQuery()) {
                        List<Warp> warps = new ArrayList<>();
                        while (rs.next()) {
                            warps.add(mapRow(rs));
                        }
                        return warps;
                    }
                });
    }

    @Override
    public CompletableFuture<Optional<Warp>> find(String name) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement("SELECT * FROM ynm_warps WHERE name = ?")) {
                        ps.setString(1, name);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? Optional.of(mapRow(rs)) : Optional.<Warp>empty();
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Void> save(Warp warp) {
        String[] updateColumns = {
            "world",
            "x",
            "y",
            "z",
            "yaw",
            "pitch",
            "category",
            "permission",
            "cost",
            "description",
            "hidden"
        };
        String upsert = dialect.upsert("ynm_warps", COLUMNS, new String[] {"name"}, updateColumns);
        return sql.run(
                connection -> {
                    try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                        int i = 1;
                        ps.setString(i++, warp.name());
                        ps.setString(i++, warp.position().worldName());
                        ps.setDouble(i++, warp.position().x());
                        ps.setDouble(i++, warp.position().y());
                        ps.setDouble(i++, warp.position().z());
                        ps.setFloat(i++, warp.position().yaw());
                        ps.setFloat(i++, warp.position().pitch());
                        ps.setString(i++, warp.category());
                        ps.setString(i++, warp.permission());
                        ps.setDouble(i++, warp.cost());
                        ps.setString(i++, warp.description());
                        ps.setBoolean(i++, warp.hidden());
                        ps.setString(
                                i++, warp.createdBy() == null ? null : warp.createdBy().toString());
                        ps.setLong(i, warp.createdAt());
                        ps.executeUpdate();
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(String name) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement("DELETE FROM ynm_warps WHERE name = ?")) {
                        ps.setString(1, name);
                        return ps.executeUpdate() > 0;
                    }
                });
    }

    private static Warp mapRow(ResultSet rs) throws java.sql.SQLException {
        String createdBy = rs.getString("created_by");
        return new Warp(
                rs.getString("name"),
                new Position(
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")),
                rs.getString("category"),
                rs.getString("permission"),
                rs.getDouble("cost"),
                rs.getString("description"),
                rs.getBoolean("hidden"),
                createdBy == null ? null : UUID.fromString(createdBy),
                rs.getLong("created_at"));
    }
}

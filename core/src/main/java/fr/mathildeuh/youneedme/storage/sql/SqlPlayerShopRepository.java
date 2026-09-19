package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.playershop.PlayerShop;
import fr.mathildeuh.youneedme.api.storage.PlayerShopRepository;
import fr.mathildeuh.youneedme.storage.util.ItemStackCodec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlPlayerShopRepository implements PlayerShopRepository {

    private final SqlExecutor sql;

    public SqlPlayerShopRepository(SqlExecutor sql) {
        this.sql = sql;
    }

    @Override
    public CompletableFuture<PlayerShop> save(PlayerShop shop) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO ynm_player_shops (owner, owner_username, sign_world, sign_x, sign_y,
                                        sign_z, chest_world, chest_x, chest_y, chest_z, item, buy_price, sell_price)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                                    Statement.RETURN_GENERATED_KEYS)) {
                        bindWithoutId(ps, shop);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            return withId(shop, keys.getLong(1));
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Void> delete(long id) {
        return sql.run(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "DELETE FROM ynm_player_shops WHERE id = ?")) {
                        ps.setLong(1, id);
                        ps.executeUpdate();
                    }
                });
    }

    @Override
    public CompletableFuture<List<PlayerShop>> findAllShops() {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                                    connection.prepareStatement("SELECT * FROM ynm_player_shops");
                            ResultSet rs = ps.executeQuery()) {
                        return mapAll(rs);
                    }
                });
    }

    @Override
    public CompletableFuture<List<PlayerShop>> findShopsByOwner(UUID owner) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_player_shops WHERE owner = ?")) {
                        ps.setString(1, owner.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    private static void bindWithoutId(PreparedStatement ps, PlayerShop shop)
            throws java.sql.SQLException {
        int i = 1;
        ps.setString(i++, shop.owner().toString());
        ps.setString(i++, shop.ownerLastKnownUsername());
        ps.setString(i++, shop.signLocation().worldName());
        ps.setInt(i++, (int) shop.signLocation().x());
        ps.setInt(i++, (int) shop.signLocation().y());
        ps.setInt(i++, (int) shop.signLocation().z());
        ps.setString(i++, shop.chestLocation().worldName());
        ps.setInt(i++, (int) shop.chestLocation().x());
        ps.setInt(i++, (int) shop.chestLocation().y());
        ps.setInt(i++, (int) shop.chestLocation().z());
        ps.setString(i++, ItemStackCodec.encode(shop.item()));
        if (shop.buyPrice() == null) {
            ps.setNull(i++, java.sql.Types.DOUBLE);
        } else {
            ps.setDouble(i++, shop.buyPrice());
        }
        if (shop.sellPrice() == null) {
            ps.setNull(i, java.sql.Types.DOUBLE);
        } else {
            ps.setDouble(i, shop.sellPrice());
        }
    }

    private static List<PlayerShop> mapAll(ResultSet rs) throws java.sql.SQLException {
        List<PlayerShop> shops = new ArrayList<>();
        while (rs.next()) {
            shops.add(mapRow(rs));
        }
        return shops;
    }

    private static PlayerShop mapRow(ResultSet rs) throws java.sql.SQLException {
        double buyPrice = rs.getDouble("buy_price");
        boolean hasBuyPrice = !rs.wasNull();
        double sellPrice = rs.getDouble("sell_price");
        boolean hasSellPrice = !rs.wasNull();
        return new PlayerShop(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner")),
                rs.getString("owner_username"),
                new Position(
                        rs.getString("sign_world"),
                        rs.getInt("sign_x"),
                        rs.getInt("sign_y"),
                        rs.getInt("sign_z"),
                        0,
                        0),
                new Position(
                        rs.getString("chest_world"),
                        rs.getInt("chest_x"),
                        rs.getInt("chest_y"),
                        rs.getInt("chest_z"),
                        0,
                        0),
                ItemStackCodec.decode(rs.getString("item")),
                hasBuyPrice ? buyPrice : null,
                hasSellPrice ? sellPrice : null);
    }

    private static PlayerShop withId(PlayerShop shop, long id) {
        return new PlayerShop(
                id,
                shop.owner(),
                shop.ownerLastKnownUsername(),
                shop.signLocation(),
                shop.chestLocation(),
                shop.item(),
                shop.buyPrice(),
                shop.sellPrice());
    }
}

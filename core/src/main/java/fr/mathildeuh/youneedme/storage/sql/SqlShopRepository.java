package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.storage.ShopRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SqlShopRepository implements ShopRepository {

    private final SqlExecutor sql;
    private final SqlDialect dialect;

    public SqlShopRepository(SqlExecutor sql, SqlDialect dialect) {
        this.sql = sql;
        this.dialect = dialect;
    }

    @Override
    public CompletableFuture<Optional<Integer>> getStock(String categoryId, String itemId) {
        return sql.submit(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT stock FROM ynm_shop_stock WHERE category_id = ? AND item_id = ?")) {
                ps.setString(1, categoryId);
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getInt(1)) : Optional.<Integer>empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> setStock(String categoryId, String itemId, int stock) {
        String upsert = dialect.upsert(
                "ynm_shop_stock",
                new String[] {"category_id", "item_id", "stock"},
                new String[] {"category_id", "item_id"},
                new String[] {"stock"});
        return sql.run(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                ps.setString(1, categoryId);
                ps.setString(2, itemId);
                ps.setInt(3, stock);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Integer> adjustStock(String categoryId, String itemId, int delta) {
        return sql.submit(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ynm_shop_stock SET stock = stock + ? WHERE category_id = ? AND item_id = ?")) {
                update.setInt(1, delta);
                update.setString(2, categoryId);
                update.setString(3, itemId);
                update.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT stock FROM ynm_shop_stock WHERE category_id = ? AND item_id = ?")) {
                select.setString(1, categoryId);
                select.setString(2, itemId);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }
}

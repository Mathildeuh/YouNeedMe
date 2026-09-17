package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import fr.mathildeuh.youneedme.api.storage.AuctionRepository;
import fr.mathildeuh.youneedme.storage.util.ItemStackCodec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlAuctionRepository implements AuctionRepository {

    private final SqlExecutor sql;

    public SqlAuctionRepository(SqlExecutor sql) {
        this.sql = sql;
    }

    @Override
    public CompletableFuture<AuctionListing> save(AuctionListing listing) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO ynm_auctions (seller, seller_username, item, price, listed_at, expires_at, status, buyer)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                                    Statement.RETURN_GENERATED_KEYS)) {
                        bindWithoutId(ps, listing);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            long id = keys.getLong(1);
                            return new AuctionListing(
                                    id,
                                    listing.seller(),
                                    listing.sellerLastKnownUsername(),
                                    listing.item(),
                                    listing.price(),
                                    listing.listedAt(),
                                    listing.expiresAt(),
                                    listing.status(),
                                    listing.buyer());
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Void> update(AuctionListing listing) {
        return sql.run(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    UPDATE ynm_auctions SET seller = ?, seller_username = ?, item = ?, price = ?, listed_at = ?,
                                        expires_at = ?, status = ?, buyer = ? WHERE id = ?
                                    """)) {
                        int i = bindWithoutId(ps, listing);
                        ps.setLong(i, listing.id());
                        ps.executeUpdate();
                    }
                });
    }

    @Override
    public CompletableFuture<Optional<AuctionListing>> find(long id) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_auctions WHERE id = ?")) {
                        ps.setLong(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next()
                                    ? Optional.of(mapRow(rs))
                                    : Optional.<AuctionListing>empty();
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findActive(int offset, int limit) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_auctions WHERE status = 'ACTIVE' ORDER BY"
                                            + " listed_at DESC LIMIT ? OFFSET ?")) {
                        ps.setInt(1, limit);
                        ps.setInt(2, offset);
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findBySeller(UUID seller, boolean activeOnly) {
        return sql.submit(
                connection -> {
                    String query =
                            activeOnly
                                    ? "SELECT * FROM ynm_auctions WHERE seller = ? AND status ="
                                            + " 'ACTIVE' ORDER BY listed_at DESC"
                                    : "SELECT * FROM ynm_auctions WHERE seller = ? ORDER BY"
                                            + " listed_at DESC";
                    try (PreparedStatement ps = connection.prepareStatement(query)) {
                        ps.setString(1, seller.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findExpiredAwaitingCollection(UUID seller) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_auctions WHERE seller = ? AND status ="
                                            + " 'EXPIRED' ORDER BY expires_at DESC")) {
                        ps.setString(1, seller.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Integer> expireOverdue() {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "UPDATE ynm_auctions SET status = 'EXPIRED' WHERE status ="
                                            + " 'ACTIVE' AND expires_at <= ?")) {
                        ps.setLong(1, System.currentTimeMillis());
                        return ps.executeUpdate();
                    }
                });
    }

    private static int bindWithoutId(PreparedStatement ps, AuctionListing listing)
            throws java.sql.SQLException {
        int i = 1;
        ps.setString(i++, listing.seller().toString());
        ps.setString(i++, listing.sellerLastKnownUsername());
        ps.setString(i++, ItemStackCodec.encode(listing.item()));
        ps.setDouble(i++, listing.price());
        ps.setLong(i++, listing.listedAt());
        ps.setLong(i++, listing.expiresAt());
        ps.setString(i++, listing.status().name());
        ps.setString(i++, listing.buyer() == null ? null : listing.buyer().toString());
        return i;
    }

    private static List<AuctionListing> mapAll(ResultSet rs) throws java.sql.SQLException {
        List<AuctionListing> listings = new ArrayList<>();
        while (rs.next()) {
            listings.add(mapRow(rs));
        }
        return listings;
    }

    private static AuctionListing mapRow(ResultSet rs) throws java.sql.SQLException {
        String buyer = rs.getString("buyer");
        return new AuctionListing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller")),
                rs.getString("seller_username"),
                ItemStackCodec.decode(rs.getString("item")),
                rs.getDouble("price"),
                rs.getLong("listed_at"),
                rs.getLong("expires_at"),
                AuctionListing.Status.valueOf(rs.getString("status")),
                buyer == null ? null : UUID.fromString(buyer));
    }
}

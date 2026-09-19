package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.storage.TicketRepository;
import fr.mathildeuh.youneedme.api.tickets.Ticket;
import fr.mathildeuh.youneedme.api.tickets.TicketMessage;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlTicketRepository implements TicketRepository {

    private final SqlExecutor sql;

    public SqlTicketRepository(SqlExecutor sql) {
        this.sql = sql;
    }

    @Override
    public CompletableFuture<Ticket> save(Ticket ticket) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO ynm_tickets (player, player_username, category, status, claimed_by,
                                        claimed_by_username, created_at, closed_at, closed_by, closed_by_username)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                                    Statement.RETURN_GENERATED_KEYS)) {
                        bindWithoutId(ps, ticket);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            return withId(ticket, keys.getLong(1));
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<Void> update(Ticket ticket) {
        return sql.run(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    UPDATE ynm_tickets SET player = ?, player_username = ?, category = ?, status = ?,
                                        claimed_by = ?, claimed_by_username = ?, created_at = ?, closed_at = ?,
                                        closed_by = ?, closed_by_username = ? WHERE id = ?
                                    """)) {
                        int i = bindWithoutId(ps, ticket);
                        ps.setLong(i, ticket.id());
                        ps.executeUpdate();
                    }
                });
    }

    @Override
    public CompletableFuture<Optional<Ticket>> findTicket(long id) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement("SELECT * FROM ynm_tickets WHERE id = ?")) {
                        ps.setLong(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<Ticket>> findByStatuses(List<Ticket.Status> statuses) {
        return sql.submit(
                connection -> {
                    String placeholders = "?, ".repeat(statuses.size());
                    placeholders = placeholders.substring(0, placeholders.length() - 2);
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_tickets WHERE status IN ("
                                            + placeholders
                                            + ") ORDER BY created_at ASC")) {
                        int i = 1;
                        for (Ticket.Status status : statuses) {
                            ps.setString(i++, status.name());
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<Ticket>> findByPlayer(UUID player) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_tickets WHERE player = ? ORDER BY"
                                            + " created_at DESC")) {
                        ps.setString(1, player.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            return mapAll(rs);
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<TicketMessage> addMessage(TicketMessage message) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO ynm_ticket_messages (ticket_id, author, author_username, staff_message,
                                        message, sent_at)
                                    VALUES (?, ?, ?, ?, ?, ?)
                                    """,
                                    Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, message.ticketId());
                        ps.setString(
                                2, message.author() == null ? null : message.author().toString());
                        ps.setString(3, message.authorUsername());
                        ps.setBoolean(4, message.staffMessage());
                        ps.setString(5, message.message());
                        ps.setLong(6, message.sentAt());
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            long id = keys.getLong(1);
                            return new TicketMessage(
                                    id,
                                    message.ticketId(),
                                    message.author(),
                                    message.authorUsername(),
                                    message.staffMessage(),
                                    message.message(),
                                    message.sentAt());
                        }
                    }
                });
    }

    @Override
    public CompletableFuture<List<TicketMessage>> messages(long ticketId) {
        return sql.submit(
                connection -> {
                    try (PreparedStatement ps =
                            connection.prepareStatement(
                                    "SELECT * FROM ynm_ticket_messages WHERE ticket_id = ? ORDER"
                                            + " BY sent_at ASC")) {
                        ps.setLong(1, ticketId);
                        try (ResultSet rs = ps.executeQuery()) {
                            List<TicketMessage> messages = new ArrayList<>();
                            while (rs.next()) {
                                String author = rs.getString("author");
                                messages.add(
                                        new TicketMessage(
                                                rs.getLong("id"),
                                                rs.getLong("ticket_id"),
                                                author == null ? null : UUID.fromString(author),
                                                rs.getString("author_username"),
                                                rs.getBoolean("staff_message"),
                                                rs.getString("message"),
                                                rs.getLong("sent_at")));
                            }
                            return messages;
                        }
                    }
                });
    }

    private static int bindWithoutId(PreparedStatement ps, Ticket ticket) throws SQLException {
        int i = 1;
        ps.setString(i++, ticket.player().toString());
        ps.setString(i++, ticket.playerLastKnownUsername());
        ps.setString(i++, ticket.category());
        ps.setString(i++, ticket.status().name());
        ps.setString(i++, ticket.claimedBy() == null ? null : ticket.claimedBy().toString());
        ps.setString(i++, ticket.claimedByUsername());
        ps.setLong(i++, ticket.createdAt());
        if (ticket.closedAt() == null) {
            ps.setNull(i++, java.sql.Types.BIGINT);
        } else {
            ps.setLong(i++, ticket.closedAt());
        }
        ps.setString(i++, ticket.closedBy() == null ? null : ticket.closedBy().toString());
        ps.setString(i++, ticket.closedByUsername());
        return i;
    }

    private static List<Ticket> mapAll(ResultSet rs) throws SQLException {
        List<Ticket> tickets = new ArrayList<>();
        while (rs.next()) {
            tickets.add(mapRow(rs));
        }
        return tickets;
    }

    private static Ticket mapRow(ResultSet rs) throws SQLException {
        String claimedBy = rs.getString("claimed_by");
        String closedBy = rs.getString("closed_by");
        long closedAt = rs.getLong("closed_at");
        boolean hasClosedAt = !rs.wasNull();
        return new Ticket(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player")),
                rs.getString("player_username"),
                rs.getString("category"),
                Ticket.Status.valueOf(rs.getString("status")),
                claimedBy == null ? null : UUID.fromString(claimedBy),
                rs.getString("claimed_by_username"),
                rs.getLong("created_at"),
                hasClosedAt ? closedAt : null,
                closedBy == null ? null : UUID.fromString(closedBy),
                rs.getString("closed_by_username"));
    }

    private static Ticket withId(Ticket ticket, long id) {
        return new Ticket(
                id,
                ticket.player(),
                ticket.playerLastKnownUsername(),
                ticket.category(),
                ticket.status(),
                ticket.claimedBy(),
                ticket.claimedByUsername(),
                ticket.createdAt(),
                ticket.closedAt(),
                ticket.closedBy(),
                ticket.closedByUsername());
    }
}

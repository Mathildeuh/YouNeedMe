package fr.mathildeuh.youneedme.storage.sql;

import java.util.List;

/**
 * Table definitions shared by every JDBC backend, parametrized only where the dialect forces it.
 */
final class SqlSchema {

    private SqlSchema() {}

    static List<String> statements(SqlDialect dialect) {
        String id = dialect.idColumnDefinition();
        String bool = dialect.booleanType();
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS ynm_player_profiles (
                    uuid VARCHAR(36) PRIMARY KEY,
                    last_username VARCHAR(16) NOT NULL,
                    nickname VARCHAR(64),
                    language_code VARCHAR(16),
                    first_joined_at BIGINT NOT NULL,
                    last_seen_at BIGINT NOT NULL,
                    playtime_seconds BIGINT NOT NULL DEFAULT 0,
                    last_world VARCHAR(64),
                    last_x DOUBLE PRECISION,
                    last_y DOUBLE PRECISION,
                    last_z DOUBLE PRECISION,
                    last_yaw REAL,
                    last_pitch REAL,
                    death_world VARCHAR(64),
                    death_x DOUBLE PRECISION,
                    death_y DOUBLE PRECISION,
                    death_z DOUBLE PRECISION,
                    death_yaw REAL,
                    death_pitch REAL
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_ynm_profiles_username ON ynm_player_profiles"
                        + " (last_username)",
                "CREATE INDEX IF NOT EXISTS idx_ynm_profiles_nickname ON ynm_player_profiles"
                        + " (nickname)",
                """
                CREATE TABLE IF NOT EXISTS ynm_homes (
                    owner VARCHAR(36) NOT NULL,
                    name VARCHAR(32) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE PRECISION NOT NULL,
                    y DOUBLE PRECISION NOT NULL,
                    z DOUBLE PRECISION NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (owner, name)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ynm_warps (
                    name VARCHAR(32) PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE PRECISION NOT NULL,
                    y DOUBLE PRECISION NOT NULL,
                    z DOUBLE PRECISION NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    category VARCHAR(32),
                    permission VARCHAR(64),
                    cost DOUBLE PRECISION NOT NULL DEFAULT 0,
                    description VARCHAR(255),
                    hidden %s NOT NULL DEFAULT %s,
                    created_by VARCHAR(36),
                    created_at BIGINT NOT NULL
                )
                """
                        .formatted(bool, dialect.isMySqlFamily() ? "0" : "FALSE"),
                """
                CREATE TABLE IF NOT EXISTS ynm_balances (
                    player VARCHAR(36) NOT NULL,
                    currency_id VARCHAR(32) NOT NULL,
                    balance DOUBLE PRECISION NOT NULL DEFAULT 0,
                    PRIMARY KEY (player, currency_id)
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_ynm_balances_currency ON ynm_balances (currency_id,"
                        + " balance)",
                """
                CREATE TABLE IF NOT EXISTS ynm_economy_log (
                    id %s,
                    player VARCHAR(36) NOT NULL,
                    currency_id VARCHAR(32) NOT NULL,
                    delta DOUBLE PRECISION NOT NULL,
                    balance_after DOUBLE PRECISION NOT NULL,
                    reason VARCHAR(64) NOT NULL,
                    related_player VARCHAR(36),
                    ts BIGINT NOT NULL
                )
                """
                        .formatted(id),
                "CREATE INDEX IF NOT EXISTS idx_ynm_economy_log_player ON ynm_economy_log (player,"
                        + " ts)",
                """
                CREATE TABLE IF NOT EXISTS ynm_kit_claims (
                    player VARCHAR(36) NOT NULL,
                    kit_id VARCHAR(32) NOT NULL,
                    claim_count BIGINT NOT NULL DEFAULT 0,
                    last_claimed_at BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (player, kit_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ynm_punishments (
                    id %s,
                    target VARCHAR(36),
                    target_ip VARCHAR(45),
                    type VARCHAR(16) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    issued_by VARCHAR(36),
                    issued_at BIGINT NOT NULL,
                    expires_at BIGINT,
                    active %s NOT NULL DEFAULT %s,
                    revoked_by VARCHAR(36),
                    revoked_at BIGINT
                )
                """
                        .formatted(id, bool, dialect.isMySqlFamily() ? "1" : "TRUE"),
                "CREATE INDEX IF NOT EXISTS idx_ynm_punishments_target ON ynm_punishments (target,"
                        + " type, active)",
                "CREATE INDEX IF NOT EXISTS idx_ynm_punishments_ip ON ynm_punishments (target_ip,"
                        + " active)",
                """
                CREATE TABLE IF NOT EXISTS ynm_auctions (
                    id %s,
                    seller VARCHAR(36) NOT NULL,
                    seller_username VARCHAR(16) NOT NULL,
                    item TEXT NOT NULL,
                    price DOUBLE PRECISION NOT NULL,
                    listed_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    buyer VARCHAR(36),
                    is_auction %s NOT NULL DEFAULT %s,
                    current_bid DOUBLE PRECISION,
                    current_bidder VARCHAR(36),
                    current_bidder_username VARCHAR(16)
                )
                """
                        .formatted(id, bool, dialect.isMySqlFamily() ? "0" : "FALSE"),
                "CREATE INDEX IF NOT EXISTS idx_ynm_auctions_status ON ynm_auctions (status,"
                        + " expires_at)",
                "CREATE INDEX IF NOT EXISTS idx_ynm_auctions_seller ON ynm_auctions (seller,"
                        + " status)",
                """
                CREATE TABLE IF NOT EXISTS ynm_shop_stock (
                    category_id VARCHAR(32) NOT NULL,
                    item_id VARCHAR(64) NOT NULL,
                    stock INT NOT NULL,
                    PRIMARY KEY (category_id, item_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ynm_player_shops (
                    id %s,
                    owner VARCHAR(36) NOT NULL,
                    owner_username VARCHAR(16) NOT NULL,
                    sign_world VARCHAR(64) NOT NULL,
                    sign_x INT NOT NULL,
                    sign_y INT NOT NULL,
                    sign_z INT NOT NULL,
                    chest_world VARCHAR(64) NOT NULL,
                    chest_x INT NOT NULL,
                    chest_y INT NOT NULL,
                    chest_z INT NOT NULL,
                    item TEXT NOT NULL,
                    buy_price DOUBLE PRECISION,
                    sell_price DOUBLE PRECISION
                )
                """
                        .formatted(id),
                "CREATE INDEX IF NOT EXISTS idx_ynm_player_shops_owner ON ynm_player_shops"
                        + " (owner)",
                """
                CREATE TABLE IF NOT EXISTS ynm_tickets (
                    id %s,
                    player VARCHAR(36) NOT NULL,
                    player_username VARCHAR(16) NOT NULL,
                    category VARCHAR(32),
                    status VARCHAR(16) NOT NULL,
                    claimed_by VARCHAR(36),
                    claimed_by_username VARCHAR(16),
                    created_at BIGINT NOT NULL,
                    closed_at BIGINT,
                    closed_by VARCHAR(36),
                    closed_by_username VARCHAR(16)
                )
                """
                        .formatted(id),
                "CREATE INDEX IF NOT EXISTS idx_ynm_tickets_status ON ynm_tickets (status,"
                        + " created_at)",
                "CREATE INDEX IF NOT EXISTS idx_ynm_tickets_player ON ynm_tickets (player,"
                        + " status)",
                """
                CREATE TABLE IF NOT EXISTS ynm_ticket_messages (
                    id %s,
                    ticket_id BIGINT NOT NULL,
                    author VARCHAR(36),
                    author_username VARCHAR(16) NOT NULL,
                    staff_message %s NOT NULL DEFAULT %s,
                    message VARCHAR(1024) NOT NULL,
                    sent_at BIGINT NOT NULL
                )
                """
                        .formatted(id, bool, dialect.isMySqlFamily() ? "0" : "FALSE"),
                "CREATE INDEX IF NOT EXISTS idx_ynm_ticket_messages_ticket ON ynm_ticket_messages"
                        + " (ticket_id, sent_at)");
    }

    /**
     * Additive columns for an install whose tables predate them - {@code CREATE TABLE IF NOT
     * EXISTS} is a no-op against an already-existing table, so a new column never reaches it that
     * way. Every statement here is expected to fail with "duplicate column" on a database that
     * already has it (either from a fresh {@link #statements} run, or a previous run of this same
     * migration) - {@code SqlStorage#migrate()} runs these best-effort, one at a time, swallowing
     * exactly that failure instead of the strict all-or-nothing handling {@link #statements} gets.
     */
    static List<String> additiveMigrations(SqlDialect dialect) {
        String bool = dialect.booleanType();
        String falseLiteral = dialect.isMySqlFamily() ? "0" : "FALSE";
        return List.of(
                "ALTER TABLE ynm_auctions ADD COLUMN is_auction "
                        + bool
                        + " NOT NULL DEFAULT "
                        + falseLiteral,
                "ALTER TABLE ynm_auctions ADD COLUMN current_bid DOUBLE PRECISION",
                "ALTER TABLE ynm_auctions ADD COLUMN current_bidder VARCHAR(36)",
                "ALTER TABLE ynm_auctions ADD COLUMN current_bidder_username VARCHAR(16)");
    }
}

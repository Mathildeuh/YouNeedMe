package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.api.storage.StorageType;

/** The handful of syntax differences between the four JDBC backends {@link SqlStorage} drives. */
public enum SqlDialect {
    // SQLite's parser only recognizes AUTOINCREMENT after the literal token "INTEGER PRIMARY KEY" -
    // BIGINT has the same storage affinity but is rejected with "AUTOINCREMENT is only allowed on
    // an INTEGER PRIMARY KEY" even though SQLite has no fixed-width integer types to begin with.
    SQLITE(StorageType.SQLITE, "org.sqlite.JDBC", "INTEGER PRIMARY KEY AUTOINCREMENT", "BOOLEAN"),
    MYSQL(
            StorageType.MYSQL,
            "org.mariadb.jdbc.Driver",
            "BIGINT PRIMARY KEY AUTO_INCREMENT",
            "BOOLEAN"),
    MARIADB(
            StorageType.MARIADB,
            "org.mariadb.jdbc.Driver",
            "BIGINT PRIMARY KEY AUTO_INCREMENT",
            "BOOLEAN"),
    POSTGRESQL(
            StorageType.POSTGRESQL,
            "org.postgresql.Driver",
            "BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY",
            "BOOLEAN");

    private final StorageType storageType;
    private final String jdbcDriver;
    private final String idColumnDefinition;
    private final String booleanType;

    SqlDialect(
            StorageType storageType,
            String jdbcDriver,
            String idColumnDefinition,
            String booleanType) {
        this.storageType = storageType;
        this.jdbcDriver = jdbcDriver;
        this.idColumnDefinition = idColumnDefinition;
        this.booleanType = booleanType;
    }

    public StorageType storageType() {
        return storageType;
    }

    public String jdbcDriver() {
        return jdbcDriver;
    }

    /**
     * Column definition for an auto-generated {@code BIGINT} primary key, e.g. punishment/auction
     * ids.
     */
    public String idColumnDefinition() {
        return idColumnDefinition;
    }

    public String booleanType() {
        return booleanType;
    }

    public boolean isMySqlFamily() {
        return this == MYSQL || this == MARIADB;
    }

    /**
     * A single-statement upsert for a table keyed by {@code keyColumns}, setting every other column
     * from {@code updateColumns} on conflict. All backends here support this shape
     * (SQLite/PostgreSQL via {@code ON CONFLICT}, MySQL/MariaDB via {@code ON DUPLICATE KEY}).
     */
    public String upsert(
            String table, String[] allColumns, String[] keyColumns, String[] updateColumns) {
        String columnList = String.join(", ", allColumns);
        String placeholders =
                String.join(", ", java.util.Collections.nCopies(allColumns.length, "?"));
        String insert =
                "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";

        if (isMySqlFamily()) {
            StringBuilder update = new StringBuilder();
            for (String column : updateColumns) {
                if (update.length() > 0) {
                    update.append(", ");
                }
                update.append(column).append(" = VALUES(").append(column).append(")");
            }
            return insert + " ON DUPLICATE KEY UPDATE " + update;
        }

        StringBuilder update = new StringBuilder();
        for (String column : updateColumns) {
            if (update.length() > 0) {
                update.append(", ");
            }
            update.append(column).append(" = excluded.").append(column);
        }
        return insert
                + " ON CONFLICT ("
                + String.join(", ", keyColumns)
                + ") DO UPDATE SET "
                + update;
    }
}

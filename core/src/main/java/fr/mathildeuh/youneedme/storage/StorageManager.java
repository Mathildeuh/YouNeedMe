package fr.mathildeuh.youneedme.storage;

import fr.mathildeuh.youneedme.api.storage.DataStorage;
import fr.mathildeuh.youneedme.api.storage.StorageType;
import fr.mathildeuh.youneedme.storage.json.JsonStorage;
import fr.mathildeuh.youneedme.storage.mongo.MongoStorage;
import fr.mathildeuh.youneedme.storage.sql.SqlConnectionConfig;
import fr.mathildeuh.youneedme.storage.sql.SqlDialect;
import fr.mathildeuh.youneedme.storage.sql.SqlStorage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Builds, connects and migrates the {@link DataStorage} selected by {@code storage.type} in {@code
 * config.yml}.
 */
public final class StorageManager {

    private final Logger logger;
    private final Path dataFolder;
    private DataStorage storage;

    public StorageManager(Logger logger, Path dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    public CompletableFuture<DataStorage> initialize(ConfigurationSection storageConfig) {
        String typeName =
                storageConfig.getString("type", "sqlite").toUpperCase(java.util.Locale.ROOT);
        StorageType type;
        try {
            type = StorageType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown storage.type '" + typeName + "', falling back to SQLite.");
            type = StorageType.SQLITE;
        }

        final StorageType resolvedType = type;
        this.storage = build(resolvedType, storageConfig);
        logger.info("Connecting to storage backend: " + storage.type() + " ...");
        return storage.connect()
                .thenCompose(v -> storage.migrate())
                .thenApply(
                        v -> {
                            logger.info("Storage backend ready (" + storage.type() + ").");
                            return storage;
                        })
                .exceptionally(
                        t -> {
                            throw new StorageException(
                                    "Failed to initialize storage backend " + resolvedType, t);
                        });
    }

    private DataStorage build(StorageType type, ConfigurationSection config) {
        return switch (type) {
            case SQLITE ->
                    new SqlStorage(
                            SqlDialect.SQLITE,
                            new SqlConnectionConfig(
                                    dataFolder.resolve(config.getString("sqlite.file", "data.db")),
                                    "",
                                    0,
                                    "",
                                    "",
                                    "",
                                    "",
                                    1));
            case MYSQL -> jdbcStorage(SqlDialect.MYSQL, config.getConfigurationSection("mysql"));
            case MARIADB ->
                    jdbcStorage(SqlDialect.MARIADB, config.getConfigurationSection("mysql"));
            case POSTGRESQL ->
                    jdbcStorage(
                            SqlDialect.POSTGRESQL, config.getConfigurationSection("postgresql"));
            case MONGODB -> {
                ConfigurationSection mongo = config.getConfigurationSection("mongodb");
                yield new MongoStorage(
                        mongo.getString("connection-string", "mongodb://localhost:27017"),
                        mongo.getString("database", "youneedme"),
                        mongo.getInt("pool-size", 10));
            }
            case JSON -> new JsonStorage(dataFolder.resolve("data").resolve("json"));
        };
    }

    private SqlStorage jdbcStorage(SqlDialect dialect, ConfigurationSection section) {
        ConfigurationSection s =
                section == null ? new org.bukkit.configuration.MemoryConfiguration() : section;
        return new SqlStorage(
                dialect,
                new SqlConnectionConfig(
                        null,
                        s.getString("host", "localhost"),
                        s.getInt("port", dialect == SqlDialect.POSTGRESQL ? 5432 : 3306),
                        s.getString("database", "youneedme"),
                        s.getString("username", "youneedme"),
                        s.getString("password", ""),
                        s.getString(
                                "parameters",
                                dialect == SqlDialect.POSTGRESQL
                                        ? ""
                                        : "useSSL=false&characterEncoding=utf8"),
                        s.getInt("pool-size", 10)));
    }

    public DataStorage storage() {
        return storage;
    }

    public CompletableFuture<Void> shutdown() {
        return storage == null ? CompletableFuture.completedFuture(null) : storage.disconnect();
    }
}

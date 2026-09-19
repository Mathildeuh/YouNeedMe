package fr.mathildeuh.youneedme.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.youneedme.api.storage.AuctionRepository;
import fr.mathildeuh.youneedme.api.storage.DataStorage;
import fr.mathildeuh.youneedme.api.storage.EconomyRepository;
import fr.mathildeuh.youneedme.api.storage.HomeRepository;
import fr.mathildeuh.youneedme.api.storage.KitRepository;
import fr.mathildeuh.youneedme.api.storage.PlayerProfileRepository;
import fr.mathildeuh.youneedme.api.storage.PunishmentRepository;
import fr.mathildeuh.youneedme.api.storage.ShopRepository;
import fr.mathildeuh.youneedme.api.storage.StorageType;
import fr.mathildeuh.youneedme.api.storage.WarpRepository;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link DataStorage} for every JDBC-backed dialect: SQLite, MySQL, MariaDB and PostgreSQL. */
public final class SqlStorage implements DataStorage {

    private final SqlDialect dialect;
    private final SqlConnectionConfig config;

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private SqlExecutor sql;

    private PlayerProfileRepository playerProfiles;
    private HomeRepository homes;
    private WarpRepository warps;
    private EconomyRepository economy;
    private KitRepository kits;
    private PunishmentRepository punishments;
    private AuctionRepository auctions;
    private ShopRepository shop;
    private fr.mathildeuh.youneedme.api.storage.PlayerShopRepository playerShops;

    public SqlStorage(SqlDialect dialect, SqlConnectionConfig config) {
        this.dialect = dialect;
        this.config = config;
    }

    @Override
    public StorageType type() {
        return dialect.storageType();
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(
                () -> {
                    HikariConfig hikariConfig = new HikariConfig();
                    hikariConfig.setJdbcUrl(buildJdbcUrl());
                    hikariConfig.setDriverClassName(dialect.jdbcDriver());
                    if (dialect != SqlDialect.SQLITE) {
                        hikariConfig.setUsername(config.username());
                        hikariConfig.setPassword(config.password());
                    }
                    int threads = dialect == SqlDialect.SQLITE ? 1 : Math.max(2, config.poolSize());
                    hikariConfig.setMaximumPoolSize(threads);
                    hikariConfig.setPoolName("YouNeedMe-" + dialect.name());
                    hikariConfig.setMinimumIdle(1);

                    this.dataSource = new HikariDataSource(hikariConfig);
                    AtomicInteger counter = new AtomicInteger();
                    this.executor =
                            Executors.newFixedThreadPool(
                                    threads,
                                    runnable -> {
                                        Thread thread =
                                                new Thread(
                                                        runnable,
                                                        "YouNeedMe-SQL-"
                                                                + counter.incrementAndGet());
                                        thread.setDaemon(true);
                                        return thread;
                                    });
                    this.sql = new SqlExecutor(dataSource, executor);

                    this.playerProfiles = new SqlPlayerProfileRepository(sql, dialect);
                    this.homes = new SqlHomeRepository(sql, dialect);
                    this.warps = new SqlWarpRepository(sql, dialect);
                    this.economy = new SqlEconomyRepository(sql, dialect);
                    this.kits = new SqlKitRepository(sql, dialect);
                    this.punishments = new SqlPunishmentRepository(sql);
                    this.auctions = new SqlAuctionRepository(sql);
                    this.shop = new SqlShopRepository(sql, dialect);
                    this.playerShops = new SqlPlayerShopRepository(sql);
                });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(
                () -> {
                    if (executor != null) {
                        executor.shutdown();
                    }
                    if (dataSource != null) {
                        dataSource.close();
                    }
                });
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public CompletableFuture<Void> migrate() {
        return sql.run(
                connection -> {
                    try (Statement statement = connection.createStatement()) {
                        for (String ddl : SqlSchema.statements(dialect)) {
                            statement.execute(ddl);
                        }
                        for (String ddl : SqlSchema.additiveMigrations(dialect)) {
                            try {
                                statement.execute(ddl);
                            } catch (java.sql.SQLException expectedOnAlreadyMigratedInstalls) {
                                // Column already exists (fresh install created it via
                                // SqlSchema#statements, or a previous run already added it) - this
                                // path is inherently best-effort, see additiveMigrations' javadoc.
                            }
                        }
                    }
                });
    }

    private String buildJdbcUrl() {
        return switch (dialect) {
            case SQLITE -> "jdbc:sqlite:" + config.sqliteFile();
            case MYSQL, MARIADB ->
                    "jdbc:mariadb://%s:%d/%s?%s"
                            .formatted(
                                    config.host(),
                                    config.port(),
                                    config.database(),
                                    config.extraParameters());
            case POSTGRESQL ->
                    "jdbc:postgresql://%s:%d/%s?%s"
                            .formatted(
                                    config.host(),
                                    config.port(),
                                    config.database(),
                                    config.extraParameters());
        };
    }

    /** Runs a single blocking {@code SELECT 1}-style check, for {@code /mysql test}. */
    public CompletableFuture<Boolean> testConnection() {
        return sql.submit(connection -> connection.isValid(5)).exceptionally(t -> false);
    }

    @Override
    public PlayerProfileRepository playerProfiles() {
        return playerProfiles;
    }

    @Override
    public HomeRepository homes() {
        return homes;
    }

    @Override
    public WarpRepository warps() {
        return warps;
    }

    @Override
    public EconomyRepository economy() {
        return economy;
    }

    @Override
    public KitRepository kits() {
        return kits;
    }

    @Override
    public PunishmentRepository punishments() {
        return punishments;
    }

    @Override
    public AuctionRepository auctions() {
        return auctions;
    }

    @Override
    public ShopRepository shop() {
        return shop;
    }

    @Override
    public fr.mathildeuh.youneedme.api.storage.PlayerShopRepository playerShops() {
        return playerShops;
    }

    public int activeConnections() {
        return dataSource == null ? 0 : dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public int idleConnections() {
        return dataSource == null ? 0 : dataSource.getHikariPoolMXBean().getIdleConnections();
    }

    public int threadsAwaitingConnection() {
        return dataSource == null
                ? 0
                : dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
    }
}

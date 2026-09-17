package fr.mathildeuh.youneedme.storage.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.kits.KitClaimState;
import fr.mathildeuh.youneedme.api.model.Home;
import fr.mathildeuh.youneedme.api.model.PlayerProfile;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.model.Warp;
import fr.mathildeuh.youneedme.api.moderation.Punishment;
import fr.mathildeuh.youneedme.api.moderation.PunishmentType;
import fr.mathildeuh.youneedme.api.storage.AuctionRepository;
import fr.mathildeuh.youneedme.api.storage.DataStorage;
import fr.mathildeuh.youneedme.api.storage.EconomyRepository;
import fr.mathildeuh.youneedme.api.storage.EconomyTransactionLog;
import fr.mathildeuh.youneedme.api.storage.HomeRepository;
import fr.mathildeuh.youneedme.api.storage.KitRepository;
import fr.mathildeuh.youneedme.api.storage.PlayerProfileRepository;
import fr.mathildeuh.youneedme.api.storage.PunishmentRepository;
import fr.mathildeuh.youneedme.api.storage.ShopRepository;
import fr.mathildeuh.youneedme.api.storage.StorageType;
import fr.mathildeuh.youneedme.api.storage.WarpRepository;
import fr.mathildeuh.youneedme.storage.util.ItemStackCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * {@link DataStorage} over MongoDB: one document per aggregate, a flat collection per repository.
 */
public final class MongoStorage
        implements DataStorage,
                PlayerProfileRepository,
                HomeRepository,
                WarpRepository,
                EconomyRepository,
                KitRepository,
                PunishmentRepository,
                AuctionRepository,
                ShopRepository {

    private final String connectionString;
    private final String databaseName;
    private final int poolSize;

    private MongoClient client;
    private MongoDatabase database;
    private ExecutorService executor;

    private MongoCollection<Document> profiles;
    private MongoCollection<Document> homes;
    private MongoCollection<Document> warps;
    private MongoCollection<Document> balances;
    private MongoCollection<Document> economyLog;
    private MongoCollection<Document> kitClaims;
    private MongoCollection<Document> punishments;
    private MongoCollection<Document> auctions;
    private MongoCollection<Document> shopStock;
    private MongoCollection<Document> counters;

    public MongoStorage(String connectionString, String databaseName, int poolSize) {
        this.connectionString = connectionString;
        this.databaseName = databaseName;
        this.poolSize = poolSize;
    }

    @Override
    public StorageType type() {
        return StorageType.MONGODB;
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(
                () -> {
                    this.client = MongoClients.create(connectionString);
                    this.database = client.getDatabase(databaseName);
                    this.executor =
                            Executors.newFixedThreadPool(
                                    Math.max(2, poolSize),
                                    r -> {
                                        Thread t = new Thread(r, "YouNeedMe-Mongo-IO");
                                        t.setDaemon(true);
                                        return t;
                                    });
                    profiles = database.getCollection("player_profiles");
                    homes = database.getCollection("homes");
                    warps = database.getCollection("warps");
                    balances = database.getCollection("balances");
                    economyLog = database.getCollection("economy_log");
                    kitClaims = database.getCollection("kit_claims");
                    punishments = database.getCollection("punishments");
                    auctions = database.getCollection("auctions");
                    shopStock = database.getCollection("shop_stock");
                    counters = database.getCollection("counters");
                });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(
                () -> {
                    if (executor != null) {
                        executor.shutdown();
                    }
                    if (client != null) {
                        client.close();
                    }
                });
    }

    @Override
    public boolean isConnected() {
        return client != null;
    }

    @Override
    public CompletableFuture<Void> migrate() {
        return CompletableFuture.runAsync(
                () -> {
                    profiles.createIndex(Indexes.ascending("lastUsername"));
                    profiles.createIndex(Indexes.ascending("nickname"));
                    homes.createIndex(
                            Indexes.ascending("owner", "name"), new IndexOptions().unique(true));
                    balances.createIndex(
                            Indexes.ascending("player", "currencyId"),
                            new IndexOptions().unique(true));
                    kitClaims.createIndex(
                            Indexes.ascending("player", "kitId"), new IndexOptions().unique(true));
                    punishments.createIndex(Indexes.ascending("target", "type", "active"));
                    punishments.createIndex(Indexes.ascending("targetIp", "active"));
                    auctions.createIndex(Indexes.ascending("status", "expiresAt"));
                    shopStock.createIndex(
                            Indexes.ascending("categoryId", "itemId"),
                            new IndexOptions().unique(true));
                },
                executor);
    }

    private long nextSequence(String name) {
        Document result =
                counters.findOneAndUpdate(
                        com.mongodb.client.model.Filters.eq("_id", name),
                        Updates.inc("seq", 1L),
                        new FindOneAndUpdateOptions()
                                .upsert(true)
                                .returnDocument(ReturnDocument.AFTER));
        return result.getLong("seq");
    }

    @Override
    public PlayerProfileRepository playerProfiles() {
        return this;
    }

    @Override
    public HomeRepository homes() {
        return this;
    }

    @Override
    public WarpRepository warps() {
        return this;
    }

    @Override
    public EconomyRepository economy() {
        return this;
    }

    @Override
    public KitRepository kits() {
        return this;
    }

    @Override
    public PunishmentRepository punishments() {
        return this;
    }

    @Override
    public AuctionRepository auctions() {
        return this;
    }

    @Override
    public ShopRepository shop() {
        return this;
    }

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    // --- PlayerProfileRepository --------------------------------------------------------------

    @Override
    public CompletableFuture<PlayerProfile> findOrCreate(UUID uuid, String currentUsername) {
        return find(uuid)
                .thenCompose(
                        existing -> {
                            long now = System.currentTimeMillis();
                            PlayerProfile profile =
                                    existing.map(
                                                    p ->
                                                            p.lastKnownUsername()
                                                                            .equals(currentUsername)
                                                                    ? p
                                                                    : new PlayerProfile(
                                                                            uuid,
                                                                            currentUsername,
                                                                            p.nickname(),
                                                                            p.languageCode(),
                                                                            p.firstJoinedAt(),
                                                                            p.lastSeenAt(),
                                                                            p.playtimeSeconds(),
                                                                            p.lastLocation(),
                                                                            p.lastDeathLocation()))
                                            .orElseGet(
                                                    () ->
                                                            new PlayerProfile(
                                                                    uuid,
                                                                    currentUsername,
                                                                    null,
                                                                    null,
                                                                    now,
                                                                    now,
                                                                    0,
                                                                    null,
                                                                    null));
                            return save(profile).thenApply(v -> profile);
                        });
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> find(UUID uuid) {
        return supplyAsync(
                () ->
                        Optional.ofNullable(
                                        profiles.find(
                                                        com.mongodb.client.model.Filters.eq(
                                                                "_id", uuid.toString()))
                                                .first())
                                .map(MongoStorage::mapProfile));
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuidByUsername(String username) {
        return supplyAsync(
                () -> {
                    Document doc =
                            profiles.find(
                                            com.mongodb.client.model.Filters.regex(
                                                    "lastUsername",
                                                    "^"
                                                            + java.util.regex.Pattern.quote(
                                                                    username)
                                                            + "$",
                                                    "i"))
                                    .first();
                    return doc == null
                            ? Optional.empty()
                            : Optional.of(UUID.fromString(doc.getString("_id")));
                });
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuidByNickname(String nickname) {
        return supplyAsync(
                () -> {
                    Document doc =
                            profiles.find(
                                            com.mongodb.client.model.Filters.regex(
                                                    "nickname",
                                                    "^"
                                                            + java.util.regex.Pattern.quote(
                                                                    nickname)
                                                            + "$",
                                                    "i"))
                                    .first();
                    return doc == null
                            ? Optional.empty()
                            : Optional.of(UUID.fromString(doc.getString("_id")));
                });
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        Document doc =
                new Document("_id", profile.uuid().toString())
                        .append("lastUsername", profile.lastKnownUsername())
                        .append("nickname", profile.nickname())
                        .append("languageCode", profile.languageCode())
                        .append("firstJoinedAt", profile.firstJoinedAt())
                        .append("lastSeenAt", profile.lastSeenAt())
                        .append("playtimeSeconds", profile.playtimeSeconds())
                        .append("lastLocation", toDocument(profile.lastLocation()))
                        .append("deathLocation", toDocument(profile.lastDeathLocation()));
        return runAsync(
                () ->
                        profiles.replaceOne(
                                com.mongodb.client.model.Filters.eq(
                                        "_id", profile.uuid().toString()),
                                doc,
                                new com.mongodb.client.model.ReplaceOptions().upsert(true)));
    }

    private static PlayerProfile mapProfile(Document doc) {
        return new PlayerProfile(
                UUID.fromString(doc.getString("_id")),
                doc.getString("lastUsername"),
                doc.getString("nickname"),
                doc.getString("languageCode"),
                doc.getLong("firstJoinedAt"),
                doc.getLong("lastSeenAt"),
                doc.get("playtimeSeconds") == null ? 0 : doc.getLong("playtimeSeconds"),
                fromDocument(doc.get("lastLocation", Document.class)),
                fromDocument(doc.get("deathLocation", Document.class)));
    }

    // --- HomeRepository ------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<Home>> findByOwner(UUID owner) {
        return supplyAsync(
                () -> {
                    List<Home> result = new ArrayList<>();
                    for (Document doc :
                            homes.find(
                                    com.mongodb.client.model.Filters.eq(
                                            "owner", owner.toString()))) {
                        result.add(mapHome(doc));
                    }
                    result.sort(java.util.Comparator.comparing(Home::name));
                    return result;
                });
    }

    @Override
    public CompletableFuture<Optional<Home>> find(UUID owner, String name) {
        return supplyAsync(
                () ->
                        Optional.ofNullable(homes.find(homeKey(owner, name)).first())
                                .map(MongoStorage::mapHome));
    }

    @Override
    public CompletableFuture<Void> save(Home home) {
        Document doc =
                new Document("owner", home.owner().toString())
                        .append("name", home.name())
                        .append("position", toDocument(home.position()))
                        .append("createdAt", home.createdAt())
                        .append("updatedAt", home.updatedAt());
        return runAsync(
                () ->
                        homes.replaceOne(
                                homeKey(home.owner(), home.name()),
                                doc,
                                new com.mongodb.client.model.ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID owner, String name) {
        return supplyAsync(() -> homes.deleteOne(homeKey(owner, name)).getDeletedCount() > 0);
    }

    @Override
    public CompletableFuture<Integer> count(UUID owner) {
        return supplyAsync(
                () ->
                        (int)
                                homes.countDocuments(
                                        com.mongodb.client.model.Filters.eq(
                                                "owner", owner.toString())));
    }

    private static Bson homeKey(UUID owner, String name) {
        return com.mongodb.client.model.Filters.and(
                com.mongodb.client.model.Filters.eq("owner", owner.toString()),
                com.mongodb.client.model.Filters.eq("name", name));
    }

    private static Home mapHome(Document doc) {
        return new Home(
                UUID.fromString(doc.getString("owner")),
                doc.getString("name"),
                fromDocument(doc.get("position", Document.class)),
                doc.getLong("createdAt"),
                doc.getLong("updatedAt"));
    }

    // --- WarpRepository ------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<Warp>> findAll() {
        return supplyAsync(
                () -> {
                    List<Warp> result = new ArrayList<>();
                    for (Document doc : warps.find()) {
                        result.add(mapWarp(doc));
                    }
                    result.sort(java.util.Comparator.comparing(Warp::name));
                    return result;
                });
    }

    @Override
    public CompletableFuture<Optional<Warp>> find(String name) {
        return supplyAsync(
                () ->
                        Optional.ofNullable(
                                        warps.find(com.mongodb.client.model.Filters.eq("_id", name))
                                                .first())
                                .map(MongoStorage::mapWarp));
    }

    @Override
    public CompletableFuture<Void> save(Warp warp) {
        Document doc =
                new Document("_id", warp.name())
                        .append("position", toDocument(warp.position()))
                        .append("category", warp.category())
                        .append("permission", warp.permission())
                        .append("cost", warp.cost())
                        .append("description", warp.description())
                        .append("hidden", warp.hidden())
                        .append(
                                "createdBy",
                                warp.createdBy() == null ? null : warp.createdBy().toString())
                        .append("createdAt", warp.createdAt());
        return runAsync(
                () ->
                        warps.replaceOne(
                                com.mongodb.client.model.Filters.eq("_id", warp.name()),
                                doc,
                                new com.mongodb.client.model.ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> delete(String name) {
        return supplyAsync(
                () ->
                        warps.deleteOne(com.mongodb.client.model.Filters.eq("_id", name))
                                        .getDeletedCount()
                                > 0);
    }

    private static Warp mapWarp(Document doc) {
        String createdBy = doc.getString("createdBy");
        return new Warp(
                doc.getString("_id"),
                fromDocument(doc.get("position", Document.class)),
                doc.getString("category"),
                doc.getString("permission"),
                doc.get("cost") == null ? 0 : doc.getDouble("cost"),
                doc.getString("description"),
                Boolean.TRUE.equals(doc.getBoolean("hidden")),
                createdBy == null ? null : UUID.fromString(createdBy),
                doc.getLong("createdAt"));
    }

    // --- EconomyRepository ---------------------------------------------------------------------

    @Override
    public CompletableFuture<Double> getBalance(
            UUID player, String currencyId, double defaultBalance) {
        return supplyAsync(
                () -> {
                    Document doc = balances.find(balanceKey(player, currencyId)).first();
                    return doc == null ? defaultBalance : doc.getDouble("balance");
                });
    }

    @Override
    public CompletableFuture<Double> applyDelta(
            UUID player, String currencyId, double delta, double defaultBalance) {
        return supplyAsync(
                () -> {
                    Document result =
                            balances.findOneAndUpdate(
                                    balanceKey(player, currencyId),
                                    java.util.List.of(
                                            new Document(
                                                    "$set",
                                                    new Document("player", player.toString())
                                                            .append("currencyId", currencyId)
                                                            .append(
                                                                    "balance",
                                                                    new Document(
                                                                            "$add",
                                                                            java.util.List.of(
                                                                                    new Document(
                                                                                            "$ifNull",
                                                                                            java
                                                                                                    .util
                                                                                                    .List
                                                                                                    .of(
                                                                                                            "$balance",
                                                                                                            defaultBalance)),
                                                                                    delta))))),
                                    new FindOneAndUpdateOptions()
                                            .upsert(true)
                                            .returnDocument(ReturnDocument.AFTER));
                    return result.getDouble("balance");
                });
    }

    @Override
    public CompletableFuture<Void> setBalance(UUID player, String currencyId, double amount) {
        Document doc =
                new Document("player", player.toString())
                        .append("currencyId", currencyId)
                        .append("balance", amount);
        return runAsync(
                () ->
                        balances.replaceOne(
                                balanceKey(player, currencyId),
                                doc,
                                new com.mongodb.client.model.ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<List<EconomyService.BalanceEntry>> top(
            String currencyId, int offset, int limit) {
        return supplyAsync(
                () -> {
                    List<EconomyService.BalanceEntry> result = new ArrayList<>();
                    for (Document doc :
                            balances.find(
                                            com.mongodb.client.model.Filters.eq(
                                                    "currencyId", currencyId))
                                    .sort(com.mongodb.client.model.Sorts.descending("balance"))
                                    .skip(offset)
                                    .limit(limit)) {
                        UUID uuid = UUID.fromString(doc.getString("player"));
                        Document profile =
                                profiles.find(
                                                com.mongodb.client.model.Filters.eq(
                                                        "_id", uuid.toString()))
                                        .first();
                        String username =
                                profile == null
                                        ? uuid.toString()
                                        : profile.getString("lastUsername");
                        result.add(
                                new EconomyService.BalanceEntry(
                                        uuid, username, doc.getDouble("balance")));
                    }
                    return result;
                });
    }

    @Override
    public CompletableFuture<Void> logTransaction(EconomyTransactionLog log) {
        Document doc =
                new Document("player", log.player().toString())
                        .append("currencyId", log.currencyId())
                        .append("delta", log.delta())
                        .append("balanceAfter", log.balanceAfter())
                        .append("reason", log.reason())
                        .append(
                                "relatedPlayer",
                                log.relatedPlayer() == null ? null : log.relatedPlayer().toString())
                        .append("ts", log.timestamp());
        return runAsync(() -> economyLog.insertOne(doc));
    }

    @Override
    public CompletableFuture<List<EconomyTransactionLog>> history(UUID player, int limit) {
        return supplyAsync(
                () -> {
                    List<EconomyTransactionLog> result = new ArrayList<>();
                    for (Document doc :
                            economyLog
                                    .find(
                                            com.mongodb.client.model.Filters.eq(
                                                    "player", player.toString()))
                                    .sort(com.mongodb.client.model.Sorts.descending("ts"))
                                    .limit(limit)) {
                        String related = doc.getString("relatedPlayer");
                        result.add(
                                new EconomyTransactionLog(
                                        0,
                                        player,
                                        doc.getString("currencyId"),
                                        doc.getDouble("delta"),
                                        doc.getDouble("balanceAfter"),
                                        doc.getString("reason"),
                                        related == null ? null : UUID.fromString(related),
                                        doc.getLong("ts")));
                    }
                    return result;
                });
    }

    private static Bson balanceKey(UUID player, String currencyId) {
        return com.mongodb.client.model.Filters.and(
                com.mongodb.client.model.Filters.eq("player", player.toString()),
                com.mongodb.client.model.Filters.eq("currencyId", currencyId));
    }

    // --- KitRepository -------------------------------------------------------------------------

    @Override
    public CompletableFuture<KitClaimState> findClaimState(UUID player, String kitId) {
        return supplyAsync(
                () -> {
                    Document doc = kitClaims.find(kitKey(player, kitId)).first();
                    return doc == null
                            ? new KitClaimState(kitId, 0, 0)
                            : new KitClaimState(
                                    kitId, doc.getLong("claimCount"), doc.getLong("lastClaimedAt"));
                });
    }

    @Override
    public CompletableFuture<Void> recordClaim(UUID player, String kitId, long timestamp) {
        return runAsync(
                () ->
                        kitClaims.findOneAndUpdate(
                                kitKey(player, kitId),
                                com.mongodb.client.model.Updates.combine(
                                        com.mongodb.client.model.Updates.setOnInsert(
                                                "player", player.toString()),
                                        com.mongodb.client.model.Updates.setOnInsert(
                                                "kitId", kitId),
                                        com.mongodb.client.model.Updates.inc("claimCount", 1L),
                                        com.mongodb.client.model.Updates.set(
                                                "lastClaimedAt", timestamp)),
                                new FindOneAndUpdateOptions().upsert(true)));
    }

    private static Bson kitKey(UUID player, String kitId) {
        return com.mongodb.client.model.Filters.and(
                com.mongodb.client.model.Filters.eq("player", player.toString()),
                com.mongodb.client.model.Filters.eq("kitId", kitId));
    }

    // --- PunishmentRepository ------------------------------------------------------------------

    @Override
    public CompletableFuture<Punishment> save(Punishment punishment) {
        return supplyAsync(
                () -> {
                    long id = punishment.id() != 0 ? punishment.id() : nextSequence("punishments");
                    Punishment assigned = withId(punishment, id);
                    punishments.insertOne(toDocument(assigned));
                    return assigned;
                });
    }

    @Override
    public CompletableFuture<Void> update(Punishment punishment) {
        return runAsync(
                () ->
                        punishments.replaceOne(
                                com.mongodb.client.model.Filters.eq("_id", punishment.id()),
                                toDocument(punishment)));
    }

    @Override
    public CompletableFuture<List<Punishment>> findByTarget(UUID target) {
        return supplyAsync(
                () ->
                        queryPunishments(
                                com.mongodb.client.model.Filters.eq("target", target.toString())));
    }

    @Override
    public CompletableFuture<List<Punishment>> findByIp(String ip) {
        return supplyAsync(
                () -> queryPunishments(com.mongodb.client.model.Filters.eq("targetIp", ip)));
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActive(UUID target, PunishmentType type) {
        return supplyAsync(
                () -> {
                    Document doc =
                            punishments
                                    .find(
                                            com.mongodb.client.model.Filters.and(
                                                    com.mongodb.client.model.Filters.eq(
                                                            "target", target.toString()),
                                                    com.mongodb.client.model.Filters.eq(
                                                            "type", type.name()),
                                                    com.mongodb.client.model.Filters.eq(
                                                            "active", true)))
                                    .sort(com.mongodb.client.model.Sorts.descending("issuedAt"))
                                    .first();
                    return Optional.ofNullable(doc).map(MongoStorage::mapPunishment);
                });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveIpBan(String ip) {
        return supplyAsync(
                () -> {
                    Document doc =
                            punishments
                                    .find(
                                            com.mongodb.client.model.Filters.and(
                                                    com.mongodb.client.model.Filters.eq(
                                                            "targetIp", ip),
                                                    com.mongodb.client.model.Filters.eq(
                                                            "type", PunishmentType.IP_BAN.name()),
                                                    com.mongodb.client.model.Filters.eq(
                                                            "active", true)))
                                    .sort(com.mongodb.client.model.Sorts.descending("issuedAt"))
                                    .first();
                    return Optional.ofNullable(doc).map(MongoStorage::mapPunishment);
                });
    }

    @Override
    public CompletableFuture<List<Punishment>> findActiveOfType(
            PunishmentType type, int offset, int limit) {
        return supplyAsync(
                () -> {
                    List<Punishment> result = new ArrayList<>();
                    for (Document doc :
                            punishments
                                    .find(
                                            com.mongodb.client.model.Filters.and(
                                                    com.mongodb.client.model.Filters.eq(
                                                            "type", type.name()),
                                                    com.mongodb.client.model.Filters.eq(
                                                            "active", true)))
                                    .sort(com.mongodb.client.model.Sorts.descending("issuedAt"))
                                    .skip(offset)
                                    .limit(limit)) {
                        result.add(mapPunishment(doc));
                    }
                    return result;
                });
    }

    private List<Punishment> queryPunishments(Bson filter) {
        List<Punishment> result = new ArrayList<>();
        for (Document doc :
                punishments
                        .find(filter)
                        .sort(com.mongodb.client.model.Sorts.descending("issuedAt"))) {
            result.add(mapPunishment(doc));
        }
        return result;
    }

    private static Document toDocument(Punishment p) {
        return new Document("_id", p.id())
                .append("target", p.target() == null ? null : p.target().toString())
                .append("targetIp", p.targetIp())
                .append("type", p.type().name())
                .append("reason", p.reason())
                .append("issuedBy", p.issuedBy() == null ? null : p.issuedBy().toString())
                .append("issuedAt", p.issuedAt())
                .append("expiresAt", p.expiresAt())
                .append("active", p.active())
                .append("revokedBy", p.revokedBy() == null ? null : p.revokedBy().toString())
                .append("revokedAt", p.revokedAt());
    }

    private static Punishment mapPunishment(Document doc) {
        String target = doc.getString("target");
        String issuedBy = doc.getString("issuedBy");
        String revokedBy = doc.getString("revokedBy");
        return new Punishment(
                doc.getLong("_id"),
                target == null ? null : UUID.fromString(target),
                doc.getString("targetIp"),
                PunishmentType.valueOf(doc.getString("type")),
                doc.getString("reason"),
                issuedBy == null ? null : UUID.fromString(issuedBy),
                doc.getLong("issuedAt"),
                doc.getLong("expiresAt"),
                Boolean.TRUE.equals(doc.getBoolean("active")),
                revokedBy == null ? null : UUID.fromString(revokedBy),
                doc.getLong("revokedAt"));
    }

    private static Punishment withId(Punishment p, long id) {
        return new Punishment(
                id,
                p.target(),
                p.targetIp(),
                p.type(),
                p.reason(),
                p.issuedBy(),
                p.issuedAt(),
                p.expiresAt(),
                p.active(),
                p.revokedBy(),
                p.revokedAt());
    }

    // --- AuctionRepository ---------------------------------------------------------------------

    @Override
    public CompletableFuture<AuctionListing> save(AuctionListing listing) {
        return supplyAsync(
                () -> {
                    long id = listing.id() != 0 ? listing.id() : nextSequence("auctions");
                    AuctionListing assigned = withId(listing, id);
                    auctions.insertOne(toDocument(assigned));
                    return assigned;
                });
    }

    @Override
    public CompletableFuture<Void> update(AuctionListing listing) {
        return runAsync(
                () ->
                        auctions.replaceOne(
                                com.mongodb.client.model.Filters.eq("_id", listing.id()),
                                toDocument(listing)));
    }

    @Override
    public CompletableFuture<Optional<AuctionListing>> find(long id) {
        return supplyAsync(
                () ->
                        Optional.ofNullable(
                                        auctions.find(
                                                        com.mongodb.client.model.Filters.eq(
                                                                "_id", id))
                                                .first())
                                .map(MongoStorage::mapAuction));
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findActive(int offset, int limit) {
        return supplyAsync(
                () -> {
                    List<AuctionListing> result = new ArrayList<>();
                    for (Document doc :
                            auctions.find(com.mongodb.client.model.Filters.eq("status", "ACTIVE"))
                                    .sort(com.mongodb.client.model.Sorts.descending("listedAt"))
                                    .skip(offset)
                                    .limit(limit)) {
                        result.add(mapAuction(doc));
                    }
                    return result;
                });
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findBySeller(UUID seller, boolean activeOnly) {
        return supplyAsync(
                () -> {
                    Bson filter =
                            activeOnly
                                    ? com.mongodb.client.model.Filters.and(
                                            com.mongodb.client.model.Filters.eq(
                                                    "seller", seller.toString()),
                                            com.mongodb.client.model.Filters.eq("status", "ACTIVE"))
                                    : com.mongodb.client.model.Filters.eq(
                                            "seller", seller.toString());
                    List<AuctionListing> result = new ArrayList<>();
                    for (Document doc :
                            auctions.find(filter)
                                    .sort(com.mongodb.client.model.Sorts.descending("listedAt"))) {
                        result.add(mapAuction(doc));
                    }
                    return result;
                });
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findExpiredAwaitingCollection(UUID seller) {
        return supplyAsync(
                () -> {
                    List<AuctionListing> result = new ArrayList<>();
                    for (Document doc :
                            auctions.find(
                                    com.mongodb.client.model.Filters.and(
                                            com.mongodb.client.model.Filters.eq(
                                                    "seller", seller.toString()),
                                            com.mongodb.client.model.Filters.eq(
                                                    "status", "EXPIRED")))) {
                        result.add(mapAuction(doc));
                    }
                    return result;
                });
    }

    @Override
    public CompletableFuture<Integer> expireOverdue() {
        return supplyAsync(
                () ->
                        (int)
                                auctions.updateMany(
                                                com.mongodb.client.model.Filters.and(
                                                        com.mongodb.client.model.Filters.eq(
                                                                "status", "ACTIVE"),
                                                        com.mongodb.client.model.Filters.lte(
                                                                "expiresAt",
                                                                System.currentTimeMillis())),
                                                com.mongodb.client.model.Updates.set(
                                                        "status", "EXPIRED"))
                                        .getModifiedCount());
    }

    private static Document toDocument(AuctionListing a) {
        return new Document("_id", a.id())
                .append("seller", a.seller().toString())
                .append("sellerUsername", a.sellerLastKnownUsername())
                .append("item", ItemStackCodec.encode(a.item()))
                .append("price", a.price())
                .append("listedAt", a.listedAt())
                .append("expiresAt", a.expiresAt())
                .append("status", a.status().name())
                .append("buyer", a.buyer() == null ? null : a.buyer().toString());
    }

    private static AuctionListing mapAuction(Document doc) {
        String buyer = doc.getString("buyer");
        return new AuctionListing(
                doc.getLong("_id"),
                UUID.fromString(doc.getString("seller")),
                doc.getString("sellerUsername"),
                ItemStackCodec.decode(doc.getString("item")),
                doc.getDouble("price"),
                doc.getLong("listedAt"),
                doc.getLong("expiresAt"),
                AuctionListing.Status.valueOf(doc.getString("status")),
                buyer == null ? null : UUID.fromString(buyer));
    }

    private static AuctionListing withId(AuctionListing a, long id) {
        return new AuctionListing(
                id,
                a.seller(),
                a.sellerLastKnownUsername(),
                a.item(),
                a.price(),
                a.listedAt(),
                a.expiresAt(),
                a.status(),
                a.buyer());
    }

    // --- ShopRepository ------------------------------------------------------------------------

    @Override
    public CompletableFuture<Optional<Integer>> getStock(String categoryId, String itemId) {
        return supplyAsync(
                () -> {
                    Document doc = shopStock.find(stockKey(categoryId, itemId)).first();
                    return doc == null ? Optional.empty() : Optional.of(doc.getInteger("stock"));
                });
    }

    @Override
    public CompletableFuture<Void> setStock(String categoryId, String itemId, int stock) {
        Document doc =
                new Document("categoryId", categoryId)
                        .append("itemId", itemId)
                        .append("stock", stock);
        return runAsync(
                () ->
                        shopStock.replaceOne(
                                stockKey(categoryId, itemId),
                                doc,
                                new com.mongodb.client.model.ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Integer> adjustStock(String categoryId, String itemId, int delta) {
        return supplyAsync(
                () -> {
                    Document result =
                            shopStock.findOneAndUpdate(
                                    stockKey(categoryId, itemId),
                                    com.mongodb.client.model.Updates.combine(
                                            com.mongodb.client.model.Updates.setOnInsert(
                                                    "categoryId", categoryId),
                                            com.mongodb.client.model.Updates.setOnInsert(
                                                    "itemId", itemId),
                                            com.mongodb.client.model.Updates.inc("stock", delta)),
                                    new FindOneAndUpdateOptions()
                                            .upsert(true)
                                            .returnDocument(ReturnDocument.AFTER));
                    return result.getInteger("stock");
                });
    }

    private static Bson stockKey(String categoryId, String itemId) {
        return com.mongodb.client.model.Filters.and(
                com.mongodb.client.model.Filters.eq("categoryId", categoryId),
                com.mongodb.client.model.Filters.eq("itemId", itemId));
    }

    // --- Position <-> Document -------------------------------------------------------------------

    private static Document toDocument(Position position) {
        if (position == null) {
            return null;
        }
        return new Document("world", position.worldName())
                .append("x", position.x())
                .append("y", position.y())
                .append("z", position.z())
                .append("yaw", (double) position.yaw())
                .append("pitch", (double) position.pitch());
    }

    private static Position fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        return new Position(
                doc.getString("world"),
                doc.getDouble("x"),
                doc.getDouble("y"),
                doc.getDouble("z"),
                doc.getDouble("yaw").floatValue(),
                doc.getDouble("pitch").floatValue());
    }
}

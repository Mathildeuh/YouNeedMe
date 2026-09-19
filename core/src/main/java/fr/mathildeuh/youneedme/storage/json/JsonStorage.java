package fr.mathildeuh.youneedme.storage.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.kits.KitClaimState;
import fr.mathildeuh.youneedme.api.model.Home;
import fr.mathildeuh.youneedme.api.model.PlayerProfile;
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
import fr.mathildeuh.youneedme.storage.StorageException;
import fr.mathildeuh.youneedme.storage.util.ItemStackCodec;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Flat-file {@link DataStorage} for single-node / debug servers: no external database, everything
 * lives under {@code plugins/YouNeedMe/data/json/}. All access is funneled through one
 * single-thread executor, which is both the concurrency control (no two operations ever race) and,
 * deliberately, this backend's scalability ceiling - it targets small servers, not clusters.
 */
public final class JsonStorage
        implements DataStorage,
                PlayerProfileRepository,
                HomeRepository,
                WarpRepository,
                EconomyRepository,
                KitRepository,
                PunishmentRepository,
                AuctionRepository,
                ShopRepository,
                fr.mathildeuh.youneedme.api.storage.PlayerShopRepository,
                fr.mathildeuh.youneedme.api.storage.TicketRepository {

    private static final TypeAdapter<UUID> UUID_ADAPTER =
            new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, UUID value) throws IOException {
                    out.value(value == null ? null : value.toString());
                }

                @Override
                public UUID read(JsonReader in) throws IOException {
                    return UUID.fromString(in.nextString());
                }
            };

    private static final TypeAdapter<org.bukkit.inventory.ItemStack> ITEM_STACK_ADAPTER =
            new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, org.bukkit.inventory.ItemStack value)
                        throws IOException {
                    out.value(ItemStackCodec.encode(value));
                }

                @Override
                public org.bukkit.inventory.ItemStack read(JsonReader in) throws IOException {
                    return ItemStackCodec.decode(in.nextString());
                }
            };

    private final Path root;
    private final Gson gson =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .registerTypeAdapter(UUID.class, UUID_ADAPTER)
                    .registerTypeAdapter(org.bukkit.inventory.ItemStack.class, ITEM_STACK_ADAPTER)
                    .create();
    private ExecutorService executor;

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Home>> homes = new ConcurrentHashMap<>();
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> balances = new ConcurrentHashMap<>();
    private final Map<UUID, List<EconomyTransactionLog>> economyLog = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, KitClaimState>> kitClaims = new ConcurrentHashMap<>();
    private final List<Punishment> punishments = new ArrayList<>();
    private final List<AuctionListing> auctions = new ArrayList<>();
    private final Map<String, Map<String, Integer>> shopStock = new ConcurrentHashMap<>();
    private final List<fr.mathildeuh.youneedme.api.playershop.PlayerShop> playerShops =
            new ArrayList<>();
    private final List<fr.mathildeuh.youneedme.api.tickets.Ticket> tickets = new ArrayList<>();
    private final List<fr.mathildeuh.youneedme.api.tickets.TicketMessage> ticketMessages =
            new ArrayList<>();
    private final AtomicLong punishmentIdSeq = new AtomicLong();
    private final AtomicLong auctionIdSeq = new AtomicLong();
    private final AtomicLong playerShopIdSeq = new AtomicLong();
    private final AtomicLong ticketIdSeq = new AtomicLong();
    private final AtomicLong ticketMessageIdSeq = new AtomicLong();

    public JsonStorage(Path root) {
        this.root = root;
    }

    // --- DataStorage -------------------------------------------------------------------------

    @Override
    public StorageType type() {
        return StorageType.JSON;
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(
                () -> {
                    this.executor =
                            Executors.newSingleThreadExecutor(
                                    r -> {
                                        Thread t = new Thread(r, "YouNeedMe-JSON-IO");
                                        t.setDaemon(true);
                                        return t;
                                    });
                });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return runAsync(
                () -> {
                    if (executor != null) {
                        executor.shutdown();
                    }
                });
    }

    @Override
    public boolean isConnected() {
        return executor != null && !executor.isShutdown();
    }

    @Override
    public CompletableFuture<Void> migrate() {
        return runAsync(
                () -> {
                    try {
                        Files.createDirectories(root);
                        profiles.putAll(loadMap(file("profiles.json"), profileMapType()));
                        warps.putAll(loadMap(file("warps.json"), warpMapType()));
                        balances.putAll(loadMap(file("balances.json"), balanceMapType()));
                        kitClaims.putAll(loadMap(file("kit_claims.json"), kitClaimMapType()));
                        shopStock.putAll(loadMap(file("shop_stock.json"), shopStockMapType()));
                        homes.putAll(loadMap(file("homes.json"), homeMapType()));
                        economyLog.putAll(loadMap(file("economy_log.json"), economyLogMapType()));
                        punishments.addAll(
                                loadList(file("punishments.json"), punishmentListType()));
                        auctions.addAll(loadList(file("auctions.json"), auctionListType()));
                        playerShops.addAll(
                                loadList(file("player_shops.json"), playerShopListType()));
                        tickets.addAll(loadList(file("tickets.json"), ticketListType()));
                        ticketMessages.addAll(
                                loadList(file("ticket_messages.json"), ticketMessageListType()));
                        punishments.stream()
                                .mapToLong(Punishment::id)
                                .max()
                                .ifPresent(max -> punishmentIdSeq.set(max));
                        auctions.stream()
                                .mapToLong(AuctionListing::id)
                                .max()
                                .ifPresent(max -> auctionIdSeq.set(max));
                        playerShops.stream()
                                .mapToLong(fr.mathildeuh.youneedme.api.playershop.PlayerShop::id)
                                .max()
                                .ifPresent(max -> playerShopIdSeq.set(max));
                        tickets.stream()
                                .mapToLong(fr.mathildeuh.youneedme.api.tickets.Ticket::id)
                                .max()
                                .ifPresent(max -> ticketIdSeq.set(max));
                        ticketMessages.stream()
                                .mapToLong(fr.mathildeuh.youneedme.api.tickets.TicketMessage::id)
                                .max()
                                .ifPresent(max -> ticketMessageIdSeq.set(max));
                    } catch (IOException e) {
                        throw new StorageException("Failed to load JSON storage from " + root, e);
                    }
                });
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
    public fr.mathildeuh.youneedme.api.storage.PlayerShopRepository playerShops() {
        return this;
    }

    @Override
    public fr.mathildeuh.youneedme.api.storage.TicketRepository tickets() {
        return this;
    }

    @Override
    public ShopRepository shop() {
        return this;
    }

    // --- PlayerProfileRepository --------------------------------------------------------------

    @Override
    public CompletableFuture<PlayerProfile> findOrCreate(UUID uuid, String currentUsername) {
        return supplyAsync(
                        () ->
                                profiles.compute(
                                        uuid,
                                        (id, existing) -> {
                                            long now = System.currentTimeMillis();
                                            if (existing == null) {
                                                return new PlayerProfile(
                                                        uuid,
                                                        currentUsername,
                                                        null,
                                                        null,
                                                        now,
                                                        now,
                                                        0,
                                                        null,
                                                        null);
                                            }
                                            return existing.lastKnownUsername()
                                                            .equals(currentUsername)
                                                    ? existing
                                                    : new PlayerProfile(
                                                            uuid,
                                                            currentUsername,
                                                            existing.nickname(),
                                                            existing.languageCode(),
                                                            existing.firstJoinedAt(),
                                                            existing.lastSeenAt(),
                                                            existing.playtimeSeconds(),
                                                            existing.lastLocation(),
                                                            existing.lastDeathLocation());
                                        }))
                .thenApply(
                        profile -> {
                            saveMap(file("profiles.json"), profiles);
                            return profile;
                        });
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> find(UUID uuid) {
        return supplyAsync(() -> Optional.ofNullable(profiles.get(uuid)));
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuidByUsername(String username) {
        return supplyAsync(
                () ->
                        profiles.values().stream()
                                .filter(p -> p.lastKnownUsername().equalsIgnoreCase(username))
                                .map(PlayerProfile::uuid)
                                .findFirst());
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuidByNickname(String nickname) {
        return supplyAsync(
                () ->
                        profiles.values().stream()
                                .filter(
                                        p ->
                                                p.nickname() != null
                                                        && p.nickname().equalsIgnoreCase(nickname))
                                .map(PlayerProfile::uuid)
                                .findFirst());
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        return runAsync(
                () -> {
                    profiles.put(profile.uuid(), profile);
                    saveMap(file("profiles.json"), profiles);
                });
    }

    // --- HomeRepository ------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<Home>> findByOwner(UUID owner) {
        return supplyAsync(
                () ->
                        homes.getOrDefault(owner, Map.of()).values().stream()
                                .sorted(Comparator.comparing(Home::name))
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Optional<Home>> find(UUID owner, String name) {
        return supplyAsync(
                () -> Optional.ofNullable(homes.getOrDefault(owner, Map.of()).get(name)));
    }

    @Override
    public CompletableFuture<Void> save(Home home) {
        return runAsync(
                () -> {
                    homes.computeIfAbsent(home.owner(), k -> new ConcurrentHashMap<>())
                            .put(home.name(), home);
                    saveMap(file("homes.json"), homes);
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID owner, String name) {
        return supplyAsync(
                () -> {
                    Map<String, Home> ownerHomes = homes.get(owner);
                    boolean removed = ownerHomes != null && ownerHomes.remove(name) != null;
                    if (removed) {
                        saveMap(file("homes.json"), homes);
                    }
                    return removed;
                });
    }

    @Override
    public CompletableFuture<Integer> count(UUID owner) {
        return supplyAsync(() -> homes.getOrDefault(owner, Map.of()).size());
    }

    // --- WarpRepository ------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<Warp>> findAll() {
        return supplyAsync(
                () ->
                        warps.values().stream()
                                .sorted(Comparator.comparing(Warp::name))
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Optional<Warp>> find(String name) {
        return supplyAsync(() -> Optional.ofNullable(warps.get(name)));
    }

    @Override
    public CompletableFuture<Void> save(Warp warp) {
        return runAsync(
                () -> {
                    warps.put(warp.name(), warp);
                    saveMap(file("warps.json"), warps);
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(String name) {
        return supplyAsync(
                () -> {
                    boolean removed = warps.remove(name) != null;
                    if (removed) {
                        saveMap(file("warps.json"), warps);
                    }
                    return removed;
                });
    }

    // --- EconomyRepository ---------------------------------------------------------------------

    @Override
    public CompletableFuture<Double> getBalance(
            UUID player, String currencyId, double defaultBalance) {
        return supplyAsync(
                () ->
                        balances.getOrDefault(player, Map.of())
                                .getOrDefault(currencyId, defaultBalance));
    }

    @Override
    public CompletableFuture<Double> applyDelta(
            UUID player, String currencyId, double delta, double defaultBalance) {
        return supplyAsync(
                () -> {
                    Map<String, Double> playerBalances =
                            balances.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                    double result =
                            playerBalances.merge(
                                    currencyId,
                                    defaultBalance + delta,
                                    (oldValue, ignored) -> oldValue + delta);
                    saveMap(file("balances.json"), balances);
                    return result;
                });
    }

    @Override
    public CompletableFuture<Void> setBalance(UUID player, String currencyId, double amount) {
        return runAsync(
                () -> {
                    balances.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                            .put(currencyId, amount);
                    saveMap(file("balances.json"), balances);
                });
    }

    @Override
    public CompletableFuture<List<EconomyService.BalanceEntry>> top(
            String currencyId, int offset, int limit) {
        return supplyAsync(
                () ->
                        balances.entrySet().stream()
                                .filter(e -> e.getValue().containsKey(currencyId))
                                .map(
                                        e ->
                                                new EconomyService.BalanceEntry(
                                                        e.getKey(),
                                                        Optional.ofNullable(
                                                                        profiles.get(e.getKey()))
                                                                .map(
                                                                        PlayerProfile
                                                                                ::lastKnownUsername)
                                                                .orElse(e.getKey().toString()),
                                                        e.getValue().get(currencyId)))
                                .sorted(
                                        Comparator.comparingDouble(
                                                        EconomyService.BalanceEntry::balance)
                                                .reversed())
                                .skip(offset)
                                .limit(limit)
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Void> logTransaction(EconomyTransactionLog log) {
        return runAsync(
                () -> {
                    List<EconomyTransactionLog> logs =
                            economyLog.computeIfAbsent(log.player(), k -> new ArrayList<>());
                    logs.add(0, log);
                    while (logs.size() > 200) {
                        logs.remove(logs.size() - 1);
                    }
                    saveMap(file("economy_log.json"), economyLog);
                });
    }

    @Override
    public CompletableFuture<List<EconomyTransactionLog>> history(UUID player, int limit) {
        return supplyAsync(
                () ->
                        economyLog.getOrDefault(player, List.of()).stream()
                                .limit(limit)
                                .collect(Collectors.toList()));
    }

    // --- KitRepository -------------------------------------------------------------------------

    @Override
    public CompletableFuture<KitClaimState> findClaimState(UUID player, String kitId) {
        return supplyAsync(
                () ->
                        kitClaims
                                .getOrDefault(player, Map.of())
                                .getOrDefault(kitId, new KitClaimState(kitId, 0, 0)));
    }

    @Override
    public CompletableFuture<Void> recordClaim(UUID player, String kitId, long timestamp) {
        return runAsync(
                () -> {
                    kitClaims
                            .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                            .merge(
                                    kitId,
                                    new KitClaimState(kitId, 1, timestamp),
                                    (oldState, ignored) ->
                                            new KitClaimState(
                                                    kitId, oldState.claimCount() + 1, timestamp));
                    saveMap(file("kit_claims.json"), kitClaims);
                });
    }

    // --- PunishmentRepository ------------------------------------------------------------------

    @Override
    public CompletableFuture<Punishment> save(Punishment punishment) {
        return supplyAsync(
                () -> {
                    Punishment assigned =
                            punishment.id() != 0
                                    ? punishment
                                    : withId(punishment, punishmentIdSeq.incrementAndGet());
                    punishments.add(assigned);
                    saveList(file("punishments.json"), punishments);
                    return assigned;
                });
    }

    @Override
    public CompletableFuture<Void> update(Punishment punishment) {
        return runAsync(
                () -> {
                    for (int i = 0; i < punishments.size(); i++) {
                        if (punishments.get(i).id() == punishment.id()) {
                            punishments.set(i, punishment);
                            break;
                        }
                    }
                    saveList(file("punishments.json"), punishments);
                });
    }

    @Override
    public CompletableFuture<List<Punishment>> findByTarget(UUID target) {
        return supplyAsync(
                () ->
                        punishments.stream()
                                .filter(p -> target.equals(p.target()))
                                .sorted(Comparator.comparingLong(Punishment::issuedAt).reversed())
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<Punishment>> findByIp(String ip) {
        return supplyAsync(
                () ->
                        punishments.stream()
                                .filter(p -> ip.equals(p.targetIp()))
                                .sorted(Comparator.comparingLong(Punishment::issuedAt).reversed())
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActive(UUID target, PunishmentType type) {
        return supplyAsync(
                () ->
                        punishments.stream()
                                .filter(
                                        p ->
                                                target.equals(p.target())
                                                        && p.type() == type
                                                        && p.active())
                                .max(Comparator.comparingLong(Punishment::issuedAt)));
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveIpBan(String ip) {
        return supplyAsync(
                () ->
                        punishments.stream()
                                .filter(
                                        p ->
                                                ip.equals(p.targetIp())
                                                        && p.type() == PunishmentType.IP_BAN
                                                        && p.active())
                                .max(Comparator.comparingLong(Punishment::issuedAt)));
    }

    @Override
    public CompletableFuture<List<Punishment>> findActiveOfType(
            PunishmentType type, int offset, int limit) {
        return supplyAsync(
                () ->
                        punishments.stream()
                                .filter(p -> p.type() == type && p.active())
                                .sorted(Comparator.comparingLong(Punishment::issuedAt).reversed())
                                .skip(offset)
                                .limit(limit)
                                .collect(Collectors.toList()));
    }

    // --- AuctionRepository ---------------------------------------------------------------------

    @Override
    public CompletableFuture<AuctionListing> save(AuctionListing listing) {
        return supplyAsync(
                () -> {
                    AuctionListing assigned =
                            listing.id() != 0
                                    ? listing
                                    : withId(listing, auctionIdSeq.incrementAndGet());
                    auctions.add(assigned);
                    saveList(file("auctions.json"), auctions);
                    return assigned;
                });
    }

    @Override
    public CompletableFuture<Void> update(AuctionListing listing) {
        return runAsync(
                () -> {
                    for (int i = 0; i < auctions.size(); i++) {
                        if (auctions.get(i).id() == listing.id()) {
                            auctions.set(i, listing);
                            break;
                        }
                    }
                    saveList(file("auctions.json"), auctions);
                });
    }

    @Override
    public CompletableFuture<Optional<AuctionListing>> find(long id) {
        return supplyAsync(() -> auctions.stream().filter(a -> a.id() == id).findFirst());
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findActive(int offset, int limit) {
        return supplyAsync(
                () ->
                        auctions.stream()
                                .filter(a -> a.status() == AuctionListing.Status.ACTIVE)
                                .sorted(
                                        Comparator.comparingLong(AuctionListing::listedAt)
                                                .reversed())
                                .skip(offset)
                                .limit(limit)
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findBySeller(UUID seller, boolean activeOnly) {
        return supplyAsync(
                () ->
                        auctions.stream()
                                .filter(a -> a.seller().equals(seller))
                                .filter(
                                        a ->
                                                !activeOnly
                                                        || a.status()
                                                                == AuctionListing.Status.ACTIVE)
                                .sorted(
                                        Comparator.comparingLong(AuctionListing::listedAt)
                                                .reversed())
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findAwaitingCollection(UUID player) {
        return supplyAsync(
                () ->
                        auctions.stream()
                                .filter(
                                        a ->
                                                (a.seller().equals(player)
                                                                && a.status()
                                                                        == AuctionListing.Status
                                                                                .EXPIRED)
                                                        || (player.equals(a.buyer())
                                                                && a.status()
                                                                        == AuctionListing.Status
                                                                                .WON))
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<AuctionListing>> findActiveExpired(long now) {
        return supplyAsync(
                () ->
                        auctions.stream()
                                .filter(
                                        a ->
                                                a.status() == AuctionListing.Status.ACTIVE
                                                        && a.expiresAt() <= now)
                                .collect(Collectors.toList()));
    }

    // --- ShopRepository ------------------------------------------------------------------------

    @Override
    public CompletableFuture<Optional<Integer>> getStock(String categoryId, String itemId) {
        return supplyAsync(
                () ->
                        Optional.ofNullable(
                                shopStock.getOrDefault(categoryId, Map.of()).get(itemId)));
    }

    @Override
    public CompletableFuture<Void> setStock(String categoryId, String itemId, int stock) {
        return runAsync(
                () -> {
                    shopStock
                            .computeIfAbsent(categoryId, k -> new ConcurrentHashMap<>())
                            .put(itemId, stock);
                    saveMap(file("shop_stock.json"), shopStock);
                });
    }

    @Override
    public CompletableFuture<Integer> adjustStock(String categoryId, String itemId, int delta) {
        return supplyAsync(
                () -> {
                    int result =
                            shopStock
                                    .computeIfAbsent(categoryId, k -> new ConcurrentHashMap<>())
                                    .merge(itemId, delta, Integer::sum);
                    saveMap(file("shop_stock.json"), shopStock);
                    return result;
                });
    }

    // --- plumbing --------------------------------------------------------------------------------

    private Path file(String name) {
        return root.resolve(name);
    }

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    private <K, V> Map<K, V> loadMap(Path path, Type type) throws IOException {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<K, V> loaded = gson.fromJson(reader, type);
            return loaded == null ? Map.of() : loaded;
        }
    }

    private <T> List<T> loadList(Path path, Type type) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<T> loaded = gson.fromJson(reader, type);
            return loaded == null ? List.of() : loaded;
        }
    }

    private void saveMap(Path path, Object map) {
        writeAtomic(path, map);
    }

    private void saveList(Path path, Object list) {
        writeAtomic(path, list);
    }

    private void writeAtomic(Path path, Object value) {
        try {
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(value, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to write " + path, e);
        }
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
                a.buyer(),
                a.auction(),
                a.currentBid(),
                a.currentBidder(),
                a.currentBidderUsername());
    }

    private static Type profileMapType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        Map.class, UUID.class, PlayerProfile.class)
                .getType();
    }

    private static Type warpMapType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        Map.class, String.class, Warp.class)
                .getType();
    }

    private static Type balanceMapType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        Map.class,
                        UUID.class,
                        com.google.gson.reflect.TypeToken.getParameterized(
                                        Map.class, String.class, Double.class)
                                .getType())
                .getType();
    }

    private static Type kitClaimMapType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        Map.class,
                        UUID.class,
                        com.google.gson.reflect.TypeToken.getParameterized(
                                        Map.class, String.class, KitClaimState.class)
                                .getType())
                .getType();
    }

    private static Type shopStockMapType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        Map.class,
                        String.class,
                        com.google.gson.reflect.TypeToken.getParameterized(
                                        Map.class, String.class, Integer.class)
                                .getType())
                .getType();
    }

    private static Type homeMapType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        Map.class,
                        UUID.class,
                        com.google.gson.reflect.TypeToken.getParameterized(
                                        Map.class, String.class, Home.class)
                                .getType())
                .getType();
    }

    private static Type economyLogMapType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        Map.class,
                        UUID.class,
                        com.google.gson.reflect.TypeToken.getParameterized(
                                        List.class, EconomyTransactionLog.class)
                                .getType())
                .getType();
    }

    private static Type punishmentListType() {
        return com.google.gson.reflect.TypeToken.getParameterized(List.class, Punishment.class)
                .getType();
    }

    private static Type auctionListType() {
        return com.google.gson.reflect.TypeToken.getParameterized(List.class, AuctionListing.class)
                .getType();
    }

    // --- PlayerShopRepository --------------------------------------------------------------------

    @Override
    public CompletableFuture<fr.mathildeuh.youneedme.api.playershop.PlayerShop> save(
            fr.mathildeuh.youneedme.api.playershop.PlayerShop shop) {
        return supplyAsync(
                () -> {
                    var assigned =
                            shop.id() != 0 ? shop : withId(shop, playerShopIdSeq.incrementAndGet());
                    playerShops.add(assigned);
                    saveList(file("player_shops.json"), playerShops);
                    return assigned;
                });
    }

    @Override
    public CompletableFuture<Void> delete(long id) {
        return runAsync(
                () -> {
                    playerShops.removeIf(s -> s.id() == id);
                    saveList(file("player_shops.json"), playerShops);
                });
    }

    @Override
    public CompletableFuture<List<fr.mathildeuh.youneedme.api.playershop.PlayerShop>>
            findAllShops() {
        return supplyAsync(() -> new ArrayList<>(playerShops));
    }

    @Override
    public CompletableFuture<List<fr.mathildeuh.youneedme.api.playershop.PlayerShop>>
            findShopsByOwner(UUID owner) {
        return supplyAsync(
                () ->
                        playerShops.stream()
                                .filter(s -> s.owner().equals(owner))
                                .collect(Collectors.toList()));
    }

    private static fr.mathildeuh.youneedme.api.playershop.PlayerShop withId(
            fr.mathildeuh.youneedme.api.playershop.PlayerShop shop, long id) {
        return new fr.mathildeuh.youneedme.api.playershop.PlayerShop(
                id,
                shop.owner(),
                shop.ownerLastKnownUsername(),
                shop.signLocation(),
                shop.chestLocation(),
                shop.item(),
                shop.buyPrice(),
                shop.sellPrice());
    }

    private static Type playerShopListType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        List.class, fr.mathildeuh.youneedme.api.playershop.PlayerShop.class)
                .getType();
    }

    // --- TicketRepository ------------------------------------------------------------------

    @Override
    public CompletableFuture<fr.mathildeuh.youneedme.api.tickets.Ticket> save(
            fr.mathildeuh.youneedme.api.tickets.Ticket ticket) {
        return supplyAsync(
                () -> {
                    var assigned = withId(ticket, ticketIdSeq.incrementAndGet());
                    tickets.add(assigned);
                    saveList(file("tickets.json"), tickets);
                    return assigned;
                });
    }

    @Override
    public CompletableFuture<Void> update(fr.mathildeuh.youneedme.api.tickets.Ticket ticket) {
        return runAsync(
                () -> {
                    for (int i = 0; i < tickets.size(); i++) {
                        if (tickets.get(i).id() == ticket.id()) {
                            tickets.set(i, ticket);
                            break;
                        }
                    }
                    saveList(file("tickets.json"), tickets);
                });
    }

    @Override
    public CompletableFuture<Optional<fr.mathildeuh.youneedme.api.tickets.Ticket>> findTicket(
            long id) {
        return supplyAsync(() -> tickets.stream().filter(t -> t.id() == id).findFirst());
    }

    @Override
    public CompletableFuture<List<fr.mathildeuh.youneedme.api.tickets.Ticket>> findByStatuses(
            List<fr.mathildeuh.youneedme.api.tickets.Ticket.Status> statuses) {
        return supplyAsync(
                () ->
                        tickets.stream()
                                .filter(t -> statuses.contains(t.status()))
                                .sorted(
                                        java.util.Comparator.comparingLong(
                                                fr.mathildeuh.youneedme.api.tickets.Ticket
                                                        ::createdAt))
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<List<fr.mathildeuh.youneedme.api.tickets.Ticket>> findByPlayer(
            UUID player) {
        return supplyAsync(
                () ->
                        tickets.stream()
                                .filter(t -> t.player().equals(player))
                                .sorted(
                                        java.util.Comparator.comparingLong(
                                                        fr.mathildeuh.youneedme.api.tickets.Ticket
                                                                ::createdAt)
                                                .reversed())
                                .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<fr.mathildeuh.youneedme.api.tickets.TicketMessage> addMessage(
            fr.mathildeuh.youneedme.api.tickets.TicketMessage message) {
        return supplyAsync(
                () -> {
                    var assigned =
                            new fr.mathildeuh.youneedme.api.tickets.TicketMessage(
                                    ticketMessageIdSeq.incrementAndGet(),
                                    message.ticketId(),
                                    message.author(),
                                    message.authorUsername(),
                                    message.staffMessage(),
                                    message.message(),
                                    message.sentAt());
                    ticketMessages.add(assigned);
                    saveList(file("ticket_messages.json"), ticketMessages);
                    return assigned;
                });
    }

    @Override
    public CompletableFuture<List<fr.mathildeuh.youneedme.api.tickets.TicketMessage>> messages(
            long ticketId) {
        return supplyAsync(
                () ->
                        ticketMessages.stream()
                                .filter(m -> m.ticketId() == ticketId)
                                .sorted(
                                        java.util.Comparator.comparingLong(
                                                fr.mathildeuh.youneedme.api.tickets.TicketMessage
                                                        ::sentAt))
                                .collect(Collectors.toList()));
    }

    private static fr.mathildeuh.youneedme.api.tickets.Ticket withId(
            fr.mathildeuh.youneedme.api.tickets.Ticket ticket, long id) {
        return new fr.mathildeuh.youneedme.api.tickets.Ticket(
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

    private static Type ticketListType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        List.class, fr.mathildeuh.youneedme.api.tickets.Ticket.class)
                .getType();
    }

    private static Type ticketMessageListType() {
        return com.google.gson.reflect.TypeToken.getParameterized(
                        List.class, fr.mathildeuh.youneedme.api.tickets.TicketMessage.class)
                .getType();
    }
}

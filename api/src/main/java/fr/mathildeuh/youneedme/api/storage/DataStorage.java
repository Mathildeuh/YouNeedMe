package fr.mathildeuh.youneedme.api.storage;

import java.util.concurrent.CompletableFuture;

/**
 * The single storage abstraction point: swapped wholesale at startup based on {@code storage.type}
 * in {@code config.yml} (SQLite by default). Every backend (SQL over HikariCP, MongoDB, or flat
 * JSON) implements this same contract, so nothing above this layer - services, commands, expansions
 * - ever needs to know which one is active.
 *
 * <p>Every operation exposed through the repositories below runs off the main/region thread; call
 * sites are expected to hop back onto the appropriate {@code SchedulerAdapter} thread themselves
 * before touching Bukkit API with the result.
 */
public interface DataStorage {

    StorageType type();

    CompletableFuture<Void> connect();

    CompletableFuture<Void> disconnect();

    boolean isConnected();

    /** Applies pending schema migrations. No-op for backends with no schema (JSON). */
    CompletableFuture<Void> migrate();

    PlayerProfileRepository playerProfiles();

    HomeRepository homes();

    WarpRepository warps();

    EconomyRepository economy();

    KitRepository kits();

    PunishmentRepository punishments();

    AuctionRepository auctions();

    ShopRepository shop();

    PlayerShopRepository playerShops();

    TicketRepository tickets();
}

package fr.mathildeuh.youneedme;

import fr.mathildeuh.youneedme.api.storage.DataStorage;
import fr.mathildeuh.youneedme.integrations.luckperms.LuckPermsHook;
import fr.mathildeuh.youneedme.modules.auctionhouse.AuctionHouseServiceImpl;
import fr.mathildeuh.youneedme.modules.economy.EconomyServiceImpl;
import fr.mathildeuh.youneedme.modules.homes.HomeServiceImpl;
import fr.mathildeuh.youneedme.modules.kits.KitServiceImpl;
import fr.mathildeuh.youneedme.modules.moderation.ModerationServiceImpl;
import fr.mathildeuh.youneedme.modules.nickname.NicknameServiceImpl;
import fr.mathildeuh.youneedme.modules.scoreboard.ScoreboardServiceImpl;
import fr.mathildeuh.youneedme.modules.shop.ShopServiceImpl;
import fr.mathildeuh.youneedme.modules.tpa.TpaModule;
import fr.mathildeuh.youneedme.modules.warps.WarpServiceImpl;
import fr.mathildeuh.youneedme.util.Cooldowns;
import fr.mathildeuh.youneedme.util.TeleportWarmup;

/**
 * Mutable holder for every module's service instance, populated once storage finishes connecting
 * (see {@code YouNeedMe#onStorageReady}) and handed out via {@code YouNeedMe#services()}. A plain
 * field bag rather than individual accessors on the plugin class on purpose - commands pull exactly
 * the services they need off one object instead of the main class growing a getter per module.
 */
public final class Services {

    public DataStorage storage;
    public EconomyServiceImpl economy;
    public HomeServiceImpl homes;
    public WarpServiceImpl warps;
    public KitServiceImpl kits;
    public AuctionHouseServiceImpl auctionHouse;
    public ShopServiceImpl shop;
    public ModerationServiceImpl moderation;
    public ScoreboardServiceImpl scoreboard;
    public NicknameServiceImpl nicknames;
    public TpaModule tpa;

    /** Null when LuckPerms isn't installed - callers fall back to plain usernames. */
    public LuckPermsHook luckPerms;

    public final Cooldowns cooldowns = new Cooldowns();
    public final TeleportWarmup warmups = new TeleportWarmup();

    /**
     * Last position before the most recent teleport/death, keyed by player - backs {@code /back}
     * and {@code /dback}.
     */
    public final java.util.Map<java.util.UUID, org.bukkit.Location> lastLocation =
            new java.util.concurrent.ConcurrentHashMap<>();

    public final java.util.Map<java.util.UUID, org.bukkit.Location> lastDeathLocation =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** AFK state for {@code /afk} and {@code /afklist}. */
    public final java.util.Set<java.util.UUID> afk =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public final java.util.Map<java.util.UUID, Long> lastActivity =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** {@code /msg} conversation partner, for {@code /reply}. */
    public final java.util.Map<java.util.UUID, java.util.UUID> lastMessaged =
            new java.util.concurrent.ConcurrentHashMap<>();

    public final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> ignoring =
            new java.util.concurrent.ConcurrentHashMap<>();
    public final java.util.Map<java.util.UUID, org.bukkit.Location> pendingSpawnConfirm =
            new java.util.concurrent.ConcurrentHashMap<>();
    public final java.util.Set<java.util.UUID> godMode =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    public final java.util.Set<java.util.UUID> vanished =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    public final java.util.Map<java.util.UUID, Long> joinedAt =
            new java.util.concurrent.ConcurrentHashMap<>();
    public final java.util.Map<java.util.UUID, String> pendingHomeDeleteConfirm =
            new java.util.concurrent.ConcurrentHashMap<>();
}

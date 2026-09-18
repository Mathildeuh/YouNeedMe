package fr.mathildeuh.youneedme;

import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.storage.DataStorage;
import fr.mathildeuh.youneedme.config.ConfigManager;
import fr.mathildeuh.youneedme.expansions.ExpansionManager;
import fr.mathildeuh.youneedme.integrations.luckperms.LuckPermsIntegration;
import fr.mathildeuh.youneedme.integrations.placeholderapi.PlaceholderApiIntegration;
import fr.mathildeuh.youneedme.integrations.vault.VaultIntegration;
import fr.mathildeuh.youneedme.lang.LanguageManager;
import fr.mathildeuh.youneedme.listener.PlayerLifecycleListener;
import fr.mathildeuh.youneedme.modules.admin.AdminModule;
import fr.mathildeuh.youneedme.modules.auctionhouse.AuctionHouseModule;
import fr.mathildeuh.youneedme.modules.discord.DiscordModule;
import fr.mathildeuh.youneedme.modules.economy.EconomyModule;
import fr.mathildeuh.youneedme.modules.homes.HomesModule;
import fr.mathildeuh.youneedme.modules.kits.KitsModule;
import fr.mathildeuh.youneedme.modules.migration.MigrationModule;
import fr.mathildeuh.youneedme.modules.moderation.ModerationModule;
import fr.mathildeuh.youneedme.modules.navigation.NavigationModule;
import fr.mathildeuh.youneedme.modules.nickname.NicknameModule;
import fr.mathildeuh.youneedme.modules.rtp.RtpModule;
import fr.mathildeuh.youneedme.modules.scoreboard.ScoreboardModule;
import fr.mathildeuh.youneedme.modules.shop.ShopModule;
import fr.mathildeuh.youneedme.modules.tpa.TpaModule;
import fr.mathildeuh.youneedme.modules.utility.UtilityModule;
import fr.mathildeuh.youneedme.modules.warps.WarpsModule;
import fr.mathildeuh.youneedme.scheduler.ServerEnvironment;
import fr.mathildeuh.youneedme.scheduler.impl.BukkitSchedulerAdapter;
import fr.mathildeuh.youneedme.scheduler.impl.FoliaSchedulerAdapter;
import fr.mathildeuh.youneedme.storage.StorageManager;
import fr.mathildeuh.youneedme.util.ResourceExtractor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class YouNeedMe extends JavaPlugin {

    private SchedulerAdapter scheduler;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private StorageManager storageManager;
    private final Services services = new Services();
    private final ExpansionManager expansionManager = new ExpansionManager(this);

    @Override
    public void onLoad() {
        this.scheduler =
                ServerEnvironment.isFolia()
                        ? new FoliaSchedulerAdapter(this)
                        : new BukkitSchedulerAdapter(this);
        getServer()
                .getServicesManager()
                .register(SchedulerAdapter.class, scheduler, this, ServicePriority.Normal);
        getLogger()
                .info(
                        "Detected server environment: "
                                + (ServerEnvironment.isFolia()
                                        ? "Folia (regionised)"
                                        : "Paper/Spigot"));
    }

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.load();

        this.languageManager = new LanguageManager(getLogger());
        ResourceExtractor.extractFolder(
                getClass(), "lang", getDataFolder().toPath().resolve("lang"), getLogger());
        languageManager.load(
                getDataFolder().toPath().resolve("lang"),
                configManager.main().getString("language.default", "en_US"));

        this.storageManager = new StorageManager(getLogger(), getDataFolder().toPath());
        storageManager
                .initialize(configManager.main().getConfigurationSection("storage"))
                .thenAccept(this::onStorageReady)
                .exceptionally(this::onStorageFailed);
    }

    private void onStorageReady(DataStorage storage) {
        scheduler.runGlobal(
                () -> {
                    getServer()
                            .getServicesManager()
                            .register(DataStorage.class, storage, this, ServicePriority.Normal);
                    services.storage = storage;

                    services.economy = EconomyModule.enable(this, storage.economy());
                    VaultIntegration.enable(this, services.economy);
                    services.homes = HomesModule.enable(this, storage.homes());
                    services.warps = WarpsModule.enable(this, storage.warps());
                    services.kits = KitsModule.enable(this, storage.kits());
                    services.auctionHouse =
                            AuctionHouseModule.enable(this, storage.auctions(), services.economy);
                    services.shop = ShopModule.enable(this, storage.shop(), services.economy);
                    services.moderation = ModerationModule.enable(this, storage.punishments());
                    services.scoreboard = ScoreboardModule.enable(this);
                    services.nicknames = NicknameModule.enable(this, storage.playerProfiles());
                    services.tpa = TpaModule.enable(this);
                    services.luckPerms = LuckPermsIntegration.enable(this);
                    PlaceholderApiIntegration.enable(this);

                    RtpModule.enable(this);
                    NavigationModule.enable(this);
                    UtilityModule.enable(this);
                    DiscordModule.enable(this);
                    MigrationModule.enable(this);
                    AdminModule.enable(this);

                    Bukkit.getPluginManager().registerEvents(services.warmups, this);
                    Bukkit.getPluginManager()
                            .registerEvents(new PlayerLifecycleListener(this), this);

                    expansionManager.loadAndEnableAll();

                    getLogger().info("YouNeedMe is ready.");
                });
    }

    private Void onStorageFailed(Throwable throwable) {
        getLogger()
                .log(
                        java.util.logging.Level.SEVERE,
                        "Could not initialize storage backend - disabling YouNeedMe",
                        throwable);
        scheduler.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
        return null;
    }

    @Override
    public void onDisable() {
        expansionManager.disableAll();
        if (storageManager != null) {
            storageManager.shutdown().join();
        }
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public LanguageManager lang() {
        return languageManager;
    }

    public StorageManager storageManager() {
        return storageManager;
    }

    public Services services() {
        return services;
    }
}

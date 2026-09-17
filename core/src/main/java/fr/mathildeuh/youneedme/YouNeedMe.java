package fr.mathildeuh.youneedme;

import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.storage.DataStorage;
import fr.mathildeuh.youneedme.config.ConfigManager;
import fr.mathildeuh.youneedme.lang.LanguageManager;
import fr.mathildeuh.youneedme.scheduler.ServerEnvironment;
import fr.mathildeuh.youneedme.scheduler.impl.BukkitSchedulerAdapter;
import fr.mathildeuh.youneedme.scheduler.impl.FoliaSchedulerAdapter;
import fr.mathildeuh.youneedme.storage.StorageManager;
import fr.mathildeuh.youneedme.util.ResourceExtractor;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class YouNeedMe extends JavaPlugin {

    private SchedulerAdapter scheduler;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private StorageManager storageManager;

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
                    getLogger().info("YouNeedMe is ready.");
                });
    }

    private Void onStorageFailed(Throwable throwable) {
        getLogger()
                .severe(
                        "Could not initialize storage backend - disabling YouNeedMe: "
                                + throwable.getMessage());
        scheduler.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
        return null;
    }

    @Override
    public void onDisable() {
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
}

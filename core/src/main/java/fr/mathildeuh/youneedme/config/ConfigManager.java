package fr.mathildeuh.youneedme.config;

import fr.mathildeuh.youneedme.util.ResourceExtractor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Owns {@code config.yml} plus every {@code modules/*.yml} file, so each feature stays in its own
 * readable file instead of one giant config. All of them reload together on {@code /ynm reload}.
 */
public final class ConfigManager {

    private static final String[] MODULE_FILES = {
        "economy",
        "shop",
        "auctionhouse",
        "homes",
        "warps",
        "rtp",
        "tpa",
        "moderation",
        "scoreboard",
        "nickname",
        "kits",
        "discord",
        "migration",
        "chat",
        "network"
    };

    private final Plugin plugin;
    private final Logger logger;
    private final Path dataFolder;
    private YamlConfiguration mainConfig;
    private final Map<String, YamlConfiguration> moduleConfigs = new LinkedHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFolder = plugin.getDataFolder().toPath();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mainConfig =
                YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));

        Path modulesDir = dataFolder.resolve("modules");
        ResourceExtractor.extractFolder(plugin.getClass(), "modules", modulesDir, logger);
        moduleConfigs.clear();
        for (String module : MODULE_FILES) {
            File file = modulesDir.resolve(module + ".yml").toFile();
            moduleConfigs.put(module, YamlConfiguration.loadConfiguration(file));
        }

        Path shopFile = dataFolder.resolve("shop.yml");
        if (!shopFile.toFile().exists()) {
            plugin.saveResource("shop.yml", false);
        }
        Path kitsFile = dataFolder.resolve("kits.yml");
        if (!kitsFile.toFile().exists()) {
            plugin.saveResource("kits.yml", false);
        }
    }

    public void reload() {
        load();
    }

    public YamlConfiguration main() {
        return mainConfig;
    }

    public YamlConfiguration module(String name) {
        YamlConfiguration config = moduleConfigs.get(name);
        if (config == null) {
            throw new IllegalArgumentException("Unknown config module: " + name);
        }
        return config;
    }

    public YamlConfiguration shop() {
        return YamlConfiguration.loadConfiguration(dataFolder.resolve("shop.yml").toFile());
    }

    public YamlConfiguration kits() {
        return YamlConfiguration.loadConfiguration(dataFolder.resolve("kits.yml").toFile());
    }

    /**
     * Persists in-game edits (the kit/shop editor GUIs) back to the file {@link #shop()}/{@link
     * #kits()} read from.
     */
    public void saveShop(YamlConfiguration config) {
        saveDataFile("shop.yml", config);
    }

    public void saveKits(YamlConfiguration config) {
        saveDataFile("kits.yml", config);
    }

    private void saveDataFile(String fileName, YamlConfiguration config) {
        try {
            config.save(dataFolder.resolve(fileName).toFile());
        } catch (IOException e) {
            logger.warning("Failed to save " + fileName + ": " + e.getMessage());
        }
    }

    public boolean isModuleEnabled(String name) {
        return mainConfig.getBoolean("modules." + name + ".enabled", true);
    }

    public void saveModule(String name) {
        try {
            moduleConfigs
                    .get(name)
                    .save(dataFolder.resolve("modules").resolve(name + ".yml").toFile());
        } catch (IOException e) {
            logger.warning("Failed to save modules/" + name + ".yml: " + e.getMessage());
        }
    }
}

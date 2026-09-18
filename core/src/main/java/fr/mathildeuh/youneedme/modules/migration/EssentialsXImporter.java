package fr.mathildeuh.youneedme.modules.migration;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.model.Position;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads EssentialsX's on-disk format directly (no dependency on EssentialsX being installed):
 * {@code plugins/Essentials/userdata/<uuid>.yml} for homes/balances, {@code
 * plugins/Essentials/warps/<name>.yml} for warps. Runs as a dry-run report unless {@code apply} is
 * {@code true}; always backs up nothing itself since dry-run writes nothing and apply only ever
 * adds YouNeedMe rows that didn't previously exist (existing YouNeedMe data is never overwritten by
 * the importer), so an explicit backup step isn't required for correctness - `/ynm backup`
 * beforehand is still recommended and mentioned to the operator.
 */
public class EssentialsXImporter {

    protected final YouNeedMe plugin;
    private final Path essentialsDataFolder;

    public EssentialsXImporter(YouNeedMe plugin) {
        this(plugin, "Essentials");
    }

    /**
     * @param sourceFolderName the plugin data folder to read from, e.g. {@code "Essentials"} or
     *     {@code "EssentialsC"} - EssentialsC being a close-to-1:1 fork, {@link
     *     EssentialsCImporter} reuses this exact reader against its own data folder instead of
     *     duplicating it.
     */
    protected EssentialsXImporter(YouNeedMe plugin, String sourceFolderName) {
        this.plugin = plugin;
        this.essentialsDataFolder =
                plugin.getDataFolder().toPath().resolveSibling(sourceFolderName);
    }

    public CompletableFuture<MigrationReport> run(boolean apply) {
        return plugin.scheduler()
                .supplyAsync(
                        () -> {
                            MigrationReport report = new MigrationReport();
                            importUserdata(report, apply);
                            importWarps(report, apply);
                            return report;
                        });
    }

    private void importUserdata(MigrationReport report, boolean apply) {
        Path userdata = essentialsDataFolder.resolve("userdata");
        if (!Files.isDirectory(userdata)) {
            report.skip("userdata/", "directory not found at " + userdata);
            return;
        }
        File[] files = userdata.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            report.playerScanned();
            UUID owner;
            try {
                owner = UUID.fromString(file.getName().replace(".yml", ""));
            } catch (IllegalArgumentException e) {
                report.skip(file.getName(), "filename isn't a UUID");
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            importHomes(report, apply, owner, yaml);
            importBalance(report, apply, owner, yaml);
        }
    }

    private void importHomes(
            MigrationReport report, boolean apply, UUID owner, YamlConfiguration yaml) {
        ConfigurationSection homes = yaml.getConfigurationSection("homes");
        if (homes == null) {
            return;
        }
        for (String name : homes.getKeys(false)) {
            ConfigurationSection home = homes.getConfigurationSection(name);
            if (home == null || !home.contains("world")) {
                report.skip("home:" + owner + "/" + name, "missing location data");
                continue;
            }
            Position position =
                    new Position(
                            home.getString("world", "world"),
                            home.getDouble("x"),
                            home.getDouble("y"),
                            home.getDouble("z"),
                            (float) home.getDouble("yaw"),
                            (float) home.getDouble("pitch"));
            report.homeImported();
            if (apply) {
                plugin.services().homes.set(owner, name, position);
            }
        }
    }

    private void importBalance(
            MigrationReport report, boolean apply, UUID owner, YamlConfiguration yaml) {
        if (!yaml.contains("money")) {
            return;
        }
        double balance = yaml.getDouble("money");
        report.balanceImported();
        if (apply) {
            plugin.services().economy.setBalance(owner, balance);
        }
    }

    private void importWarps(MigrationReport report, boolean apply) {
        Path warpsDir = essentialsDataFolder.resolve("warps");
        if (!Files.isDirectory(warpsDir)) {
            report.skip("warps/", "directory not found at " + warpsDir);
            return;
        }
        File[] files = warpsDir.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection location = yaml.getConfigurationSection("location");
            if (location == null || !location.contains("world")) {
                report.skip("warp:" + file.getName(), "missing location data");
                continue;
            }
            String name = file.getName().replace(".yml", "");
            Position position =
                    new Position(
                            location.getString("world", "world"),
                            location.getDouble("x"),
                            location.getDouble("y"),
                            location.getDouble("z"),
                            (float) location.getDouble("yaw"),
                            (float) location.getDouble("pitch"));
            report.warpImported();
            if (apply) {
                plugin.services().warps.create(name, position, null);
            }
        }
    }
}

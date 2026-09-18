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
 * Reads CMI's YAML on-disk format: {@code plugins/CMI/Users/<uuid>.yml} for homes/balances, {@code
 * plugins/CMI/warps.yml} for warps (CMI keeps every warp in one file, unlike EssentialsX). Same
 * dry-run-by-default, additive-only contract as {@link EssentialsXImporter}.
 *
 * <p>CMI can alternatively be configured to store its data in SQL rather than YAML - per the brief
 * this importer is the YAML reader only; a server running CMI on a SQL backend isn't covered here
 * and should export to YAML (CMI supports this) before migrating.
 */
public final class CmiImporter {

    private final YouNeedMe plugin;
    private final Path cmiDataFolder;

    public CmiImporter(YouNeedMe plugin) {
        this.plugin = plugin;
        this.cmiDataFolder = plugin.getDataFolder().toPath().resolveSibling("CMI");
    }

    public CompletableFuture<MigrationReport> run(boolean apply) {
        return plugin.scheduler()
                .supplyAsync(
                        () -> {
                            MigrationReport report = new MigrationReport();
                            importUsers(report, apply);
                            importWarps(report, apply);
                            return report;
                        });
    }

    private void importUsers(MigrationReport report, boolean apply) {
        Path users = cmiDataFolder.resolve("Users");
        if (!Files.isDirectory(users)) {
            report.skip("Users/", "directory not found at " + users);
            return;
        }
        File[] files = users.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
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
        ConfigurationSection homes = yaml.getConfigurationSection("Homes");
        if (homes == null) {
            return;
        }
        for (String name : homes.getKeys(false)) {
            ConfigurationSection home = homes.getConfigurationSection(name);
            Position position = readPosition(home);
            if (position == null) {
                report.skip("home:" + owner + "/" + name, "missing location data");
                continue;
            }
            report.homeImported();
            if (apply) {
                plugin.services().homes.set(owner, name, position);
            }
        }
    }

    private void importBalance(
            MigrationReport report, boolean apply, UUID owner, YamlConfiguration yaml) {
        if (!yaml.contains("Money")) {
            return;
        }
        double balance = yaml.getDouble("Money");
        report.balanceImported();
        if (apply) {
            plugin.services().economy.setBalance(owner, balance);
        }
    }

    private void importWarps(MigrationReport report, boolean apply) {
        File warpsFile = cmiDataFolder.resolve("warps.yml").toFile();
        if (!warpsFile.isFile()) {
            report.skip("warps.yml", "file not found at " + warpsFile);
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(warpsFile);
        ConfigurationSection warps = yaml.getConfigurationSection("Warps");
        if (warps == null) {
            report.skip("warps.yml", "no 'Warps' section");
            return;
        }
        for (String name : warps.getKeys(false)) {
            Position position = readPosition(warps.getConfigurationSection(name));
            if (position == null) {
                report.skip("warp:" + name, "missing location data");
                continue;
            }
            report.warpImported();
            if (apply) {
                plugin.services().warps.create(name, position, null);
            }
        }
    }

    private static Position readPosition(ConfigurationSection section) {
        if (section == null || !section.contains("World")) {
            return null;
        }
        return new Position(
                section.getString("World", "world"),
                section.getDouble("X"),
                section.getDouble("Y"),
                section.getDouble("Z"),
                (float) section.getDouble("Yaw"),
                (float) section.getDouble("Pitch"));
    }
}

package fr.mathildeuh.youneedme.modules.navigation;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class BackCommand extends YnmCommand {

    BackCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.back", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Location back = services().lastLocation.get(player.getUniqueId());
        if (back == null) {
            send(sender, "back.no_location");
            return;
        }
        if (services().cooldowns.isOnCooldown(player.getUniqueId(), "back")) {
            send(
                    sender,
                    "back.cooldown",
                    Placeholder.unparsed(
                            "seconds",
                            String.valueOf(
                                    services()
                                                    .cooldowns
                                                    .remainingMillis(player.getUniqueId(), "back")
                                            / 1000)));
            return;
        }
        Location current = player.getLocation();
        services().lastLocation.put(player.getUniqueId(), current);
        player.teleportAsync(back);
        services()
                .cooldowns
                .set(
                        player.getUniqueId(),
                        "back",
                        plugin.configManager().main().getLong("navigation.back-cooldown-seconds", 0)
                                * 1000L);
        send(sender, "back.success");
    }
}

final class DBackCommand extends YnmCommand {

    DBackCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.dback", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Location death = services().lastDeathLocation.get(player.getUniqueId());
        if (death == null) {
            send(sender, "dback.no_location");
            return;
        }
        player.teleportAsync(death);
        send(sender, "dback.success");
    }
}

final class TopCommand extends YnmCommand {

    TopCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.top", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Location loc = player.getLocation();
        Block highest = loc.getWorld().getHighestBlockAt(loc);
        player.teleportAsync(highest.getLocation().add(0.5, 1, 0.5));
    }
}

final class SpawnCommand extends YnmCommand {

    SpawnCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.spawn", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && sender.hasPermission("youneedme.spawn.others")) {
            Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
            Location spawn =
                    plugin.configManager().main().getConfigurationSection("navigation.spawn")
                                    != null
                            ? readSpawn(plugin)
                            : null;
            if (target != null && spawn != null) {
                target.teleportAsync(spawn);
                send(
                        sender,
                        "spawn.admin.teleported_other",
                        Placeholder.unparsed("player", target.getName()));
            }
            return;
        }
        Player player = player(sender);
        Location spawn = readSpawn(plugin);
        if (spawn == null) {
            send(sender, "spawn.not_set");
            return;
        }
        player.teleportAsync(spawn);
        send(sender, "spawn.success");
    }

    static Location readSpawn(YouNeedMe plugin) {
        var section = plugin.configManager().main().getConfigurationSection("navigation.spawn");
        if (section == null || !section.contains("world")) {
            return null;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }
}

final class SetSpawnCommand extends YnmCommand {

    SetSpawnCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.setspawn", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Location loc = player.getLocation();
        var config = plugin.configManager().main();
        config.set("navigation.spawn.world", loc.getWorld().getName());
        config.set("navigation.spawn.x", loc.getX());
        config.set("navigation.spawn.y", loc.getY());
        config.set("navigation.spawn.z", loc.getZ());
        config.set("navigation.spawn.yaw", (double) loc.getYaw());
        config.set("navigation.spawn.pitch", (double) loc.getPitch());
        try {
            config.save(new java.io.File(plugin.getDataFolder(), "config.yml"));
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Failed to persist spawn location: " + e.getMessage());
        }
        send(sender, "spawn.set.success");
    }
}

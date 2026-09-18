package fr.mathildeuh.youneedme.modules.rtp;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RtpCommand extends YnmCommand {

    private static final Set<Material> UNSAFE =
            Set.of(
                    Material.LAVA,
                    Material.WATER,
                    Material.FIRE,
                    Material.CACTUS,
                    Material.MAGMA_BLOCK);

    public RtpCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.rtp", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        var config = plugin.configManager().module("rtp");
        if (!config.getBoolean("enabled", true)) {
            send(sender, "rtp.error.disabled");
            return;
        }
        if (services().cooldowns.isOnCooldown(player.getUniqueId(), "rtp")
                && !player.hasPermission("youneedme.rtp.bypass-cooldown")) {
            send(
                    sender,
                    "rtp.error.cooldown",
                    Placeholder.unparsed(
                            "time",
                            String.valueOf(
                                    services()
                                                    .cooldowns
                                                    .remainingMillis(player.getUniqueId(), "rtp")
                                            / 1000)));
            return;
        }
        if (services().warmups.hasPending(player.getUniqueId())) {
            return;
        }
        World world = player.getWorld();
        int minRadius = config.getInt("min-radius", 100);
        int maxRadius = config.getInt("max-radius", 5000);
        int centerX = config.getInt("center-x", 0);
        int centerZ = config.getInt("center-z", 0);
        send(sender, "rtp.searching");
        plugin.scheduler()
                .runAsync(
                        () -> {
                            Location result = search(world, centerX, centerZ, minRadius, maxRadius);
                            plugin.scheduler()
                                    .runGlobal(() -> onLocationFound(player, world, result));
                        });
    }

    private void onLocationFound(Player player, World world, Location result) {
        if (result == null) {
            send(player, "rtp.error.no_safe_location");
            return;
        }
        int warmup = plugin.configManager().module("rtp").getInt("teleport-warmup-seconds", 3);
        if (warmup > 0) {
            send(player, "rtp.warmup.start", Placeholder.unparsed("time", String.valueOf(warmup)));
        }
        services()
                .warmups
                .start(
                        player,
                        warmup,
                        plugin.scheduler(),
                        () ->
                                plugin.scheduler()
                                        .runAtLocation(
                                                result,
                                                () -> {
                                                    player.teleportAsync(result);
                                                    services()
                                                            .cooldowns
                                                            .set(
                                                                    player.getUniqueId(),
                                                                    "rtp",
                                                                    plugin.configManager()
                                                                                    .module("rtp")
                                                                                    .getLong(
                                                                                            "cooldown-seconds",
                                                                                            0)
                                                                            * 1000L);
                                                    send(
                                                            player,
                                                            "rtp.success",
                                                            Placeholder.unparsed(
                                                                    "x",
                                                                    String.valueOf(
                                                                            result.getBlockX())),
                                                            Placeholder.unparsed(
                                                                    "y",
                                                                    String.valueOf(
                                                                            result.getBlockY())),
                                                            Placeholder.unparsed(
                                                                    "z",
                                                                    String.valueOf(
                                                                            result.getBlockZ())),
                                                            Placeholder.unparsed(
                                                                    "world", world.getName()));
                                                }),
                        () -> send(player, "rtp.warmup.cancelled"));
    }

    private static Location search(
            World world, int centerX, int centerZ, int minRadius, int maxRadius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
            int x = centerX + (int) (Math.cos(angle) * distance);
            int z = centerZ + (int) (Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) {
                continue;
            }
            Material ground = world.getBlockAt(x, y, z).getType();
            Material feet = world.getBlockAt(x, y + 1, z).getType();
            if (!ground.isSolid() || UNSAFE.contains(ground) || feet != Material.AIR) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}

package fr.mathildeuh.youneedme.modules.utility;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class AfkCommand extends YnmCommand {

    AfkCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.afk", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        boolean nowAfk = !services().afk.contains(player.getUniqueId());
        if (nowAfk) {
            services().afk.add(player.getUniqueId());
            send(sender, "afk.self.enter");
            broadcastOthers(player, "afk.broadcast.enter");
        } else {
            services().afk.remove(player.getUniqueId());
            send(sender, "afk.self.leave");
            broadcastOthers(player, "afk.broadcast.leave");
        }
    }

    private void broadcastOthers(Player player, String key) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                send(online, key, Placeholder.unparsed("player", player.getName()));
            }
        }
    }
}

final class AfkListCommand extends YnmCommand {

    AfkListCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.afklist", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (services().afk.isEmpty()) {
            send(sender, "afk.list.empty");
            return;
        }
        send(
                sender,
                "afk.list.header",
                Placeholder.unparsed("count", String.valueOf(services().afk.size())));
        for (var uuid : services().afk) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            long since = services().lastActivity.getOrDefault(uuid, System.currentTimeMillis());
            send(
                    sender,
                    "afk.list.entry",
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed(
                            "duration", TimeParser.format(System.currentTimeMillis() - since)));
        }
        send(sender, "afk.list.footer");
    }
}

final class HealCommand extends YnmCommand {

    HealCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.heal", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player target =
                args.length > 0
                        ? Bukkit.getPlayerExact(args[0])
                        : (sender instanceof Player p ? p : null);
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        var maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        target.setHealth(maxHealth != null ? maxHealth.getValue() : 20);
        target.setFoodLevel(20);
        target.setSaturation(20);
        target.setFireTicks(0);
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        if (self) {
            send(sender, "heal.success");
        } else {
            send(sender, "heal.success.others", Placeholder.unparsed("player", target.getName()));
            send(target, "heal.success.by", Placeholder.unparsed("healer", senderName(sender)));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class FeedCommand extends YnmCommand {

    FeedCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.feed", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player target =
                args.length > 0
                        ? Bukkit.getPlayerExact(args[0])
                        : (sender instanceof Player p ? p : null);
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        target.setFoodLevel(20);
        target.setSaturation(20);
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        if (self) {
            send(sender, "feed.success");
        } else {
            send(sender, "feed.success.other", Placeholder.unparsed("target", target.getName()));
            send(target, "feed.success.by", Placeholder.unparsed("feeder", senderName(sender)));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class FlyCommand extends YnmCommand {

    FlyCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.fly", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        boolean nowFlying = !player.getAllowFlight();
        player.setAllowFlight(nowFlying);
        player.setFlying(nowFlying);
        send(sender, nowFlying ? "fly.enabled" : "fly.disabled");
    }
}

final class SpeedCommand extends YnmCommand {

    SpeedCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.speed", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.speed");
            return;
        }
        String type = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("reset".equals(type)) {
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            send(sender, "speed.reset.success");
            return;
        }
        if (args.length < 2) {
            send(sender, "command.usage.speed");
            return;
        }
        float speed;
        try {
            speed = Float.parseFloat(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "speed.invalid_number");
            return;
        }
        if (speed < -1 || speed > 1) {
            send(sender, "speed.invalid_range");
            return;
        }
        if ("walkspeed".equals(type)) {
            player.setWalkSpeed(speed);
            send(
                    sender,
                    "speed.walk.success",
                    Placeholder.unparsed("speed", String.valueOf(speed)));
        } else if ("flyspeed".equals(type)) {
            player.setFlySpeed(speed);
            send(sender, "speed.fly.success", Placeholder.unparsed("speed", String.valueOf(speed)));
        } else {
            send(sender, "command.usage.speed");
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("reset", "walkspeed", "flyspeed") : List.of();
    }
}

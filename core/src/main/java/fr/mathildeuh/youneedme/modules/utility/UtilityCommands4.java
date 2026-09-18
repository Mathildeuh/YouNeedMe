package fr.mathildeuh.youneedme.modules.utility;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class PingCommand extends YnmCommand {

    PingCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.ping", false);
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
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        send(
                sender,
                self ? "ping.self" : "ping.other",
                Placeholder.unparsed("ping", String.valueOf(target.getPing())),
                Placeholder.unparsed("target", target.getName()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class PlaytimeCommand extends YnmCommand {

    PlaytimeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.playtime", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        OfflinePlayer target =
                args.length > 0
                        ? Bukkit.getOfflinePlayer(args[0])
                        : (sender instanceof Player p ? p : null);
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        services()
                .storage
                .playerProfiles()
                .find(target.getUniqueId())
                .thenAccept(
                        opt -> {
                            long seconds = opt.map(p -> p.playtimeSeconds()).orElse(0L);
                            long days = seconds / 86400;
                            long hours = (seconds % 86400) / 3600;
                            long minutes = (seconds % 3600) / 60;
                            send(
                                    sender,
                                    self ? "playtime.self" : "playtime.other",
                                    Placeholder.unparsed(
                                            "player", String.valueOf(target.getName())),
                                    Placeholder.unparsed("days", String.valueOf(days)),
                                    Placeholder.unparsed("hours", String.valueOf(hours)),
                                    Placeholder.unparsed("minutes", String.valueOf(minutes)),
                                    Placeholder.unparsed("seconds", String.valueOf(seconds % 60)));
                        });
    }
}

final class UptimeCommand extends YnmCommand {

    UptimeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.uptime", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        long uptimeMillis =
                System.currentTimeMillis()
                        - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long seconds = uptimeMillis / 1000;
        send(
                sender,
                "uptime.message",
                Placeholder.unparsed("days", String.valueOf(seconds / 86400)),
                Placeholder.unparsed("hours", String.valueOf((seconds % 86400) / 3600)),
                Placeholder.unparsed("minutes", String.valueOf((seconds % 3600) / 60)),
                Placeholder.unparsed("seconds", String.valueOf(seconds % 60)));
    }
}

final class BroadcastCommand extends YnmCommand {

    BroadcastCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.broadcast", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.broadcast");
            return;
        }
        String message = String.join(" ", args);
        Bukkit.getServer()
                .sendMessage(
                        plugin.lang()
                                .render(
                                        plugin.lang().defaultLocale(),
                                        "broadcast.format",
                                        Placeholder.unparsed("message", message)));
        send(sender, "broadcast.success");
    }
}

final class MsgCommand extends YnmCommand {

    MsgCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.msg", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length < 2) {
            send(sender, "command.usage.msg");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "msg.cannot_message_self");
            return;
        }
        if (services()
                .ignoring
                .getOrDefault(target.getUniqueId(), java.util.Set.of())
                .contains(player.getUniqueId())) {
            send(sender, "msg.ignored_by_target");
            return;
        }
        if (services()
                .ignoring
                .getOrDefault(player.getUniqueId(), java.util.Set.of())
                .contains(target.getUniqueId())) {
            send(sender, "msg.ignoring_target");
            return;
        }
        String message = String.join(" ", java.util.Arrays.asList(args).subList(1, args.length));
        services().lastMessaged.put(player.getUniqueId(), target.getUniqueId());
        services().lastMessaged.put(target.getUniqueId(), player.getUniqueId());
        send(
                sender,
                "msg.outgoing",
                Placeholder.unparsed("target", target.getName()),
                Placeholder.unparsed("message", message));
        send(
                target,
                "msg.incoming",
                Placeholder.unparsed("sender", player.getName()),
                Placeholder.unparsed("message", message));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class ReplyCommand extends YnmCommand {

    ReplyCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.reply", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.reply");
            return;
        }
        UUID lastTarget = services().lastMessaged.get(player.getUniqueId());
        if (lastTarget == null) {
            send(sender, "reply.no_reply_target");
            return;
        }
        Player target = Bukkit.getPlayer(lastTarget);
        if (target == null) {
            send(sender, "reply.target_offline");
            return;
        }
        String message = String.join(" ", args);
        send(
                sender,
                "msg.outgoing",
                Placeholder.unparsed("target", target.getName()),
                Placeholder.unparsed("message", message));
        send(
                target,
                "msg.incoming",
                Placeholder.unparsed("sender", player.getName()),
                Placeholder.unparsed("message", message));
    }
}

final class IgnoreCommand extends YnmCommand {

    IgnoreCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.ignore", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            var ignored =
                    services().ignoring.getOrDefault(player.getUniqueId(), java.util.Set.of());
            if (ignored.isEmpty()) {
                send(sender, "ignore.list_empty");
                return;
            }
            send(sender, "ignore.list_header");
            ignored.forEach(
                    uuid ->
                            send(
                                    sender,
                                    "ignore.list_entry",
                                    Placeholder.unparsed(
                                            "player",
                                            String.valueOf(
                                                    Bukkit.getOfflinePlayer(uuid).getName()))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "ignore.cannot_ignore_self");
            return;
        }
        var set =
                services()
                        .ignoring
                        .computeIfAbsent(
                                player.getUniqueId(),
                                k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (set.remove(target.getUniqueId())) {
            send(
                    sender,
                    "ignore.unignored",
                    Placeholder.unparsed("player", String.valueOf(target.getName())));
        } else {
            set.add(target.getUniqueId());
            send(
                    sender,
                    "ignore.ignored",
                    Placeholder.unparsed("player", String.valueOf(target.getName())));
        }
    }
}

final class PlayerListCommand extends YnmCommand {

    PlayerListCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.playerlist", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        var online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            send(sender, "playerlist.empty");
            return;
        }
        int perPage = 10;
        int totalPages = Math.max(1, (online.size() + perPage - 1) / perPage);
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Math.min(totalPages, Integer.parseInt(args[0])));
            } catch (NumberFormatException e) {
                send(sender, "playerlist.invalid_page");
                return;
            }
        }
        send(
                sender,
                "playerlist.header",
                Placeholder.unparsed("online", String.valueOf(online.size())),
                Placeholder.unparsed("max", String.valueOf(Bukkit.getMaxPlayers())),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("total_pages", String.valueOf(totalPages)));
        online.stream()
                .skip((long) (page - 1) * perPage)
                .limit(perPage)
                .forEach(
                        p ->
                                sender.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                                " - " + p.getName())));
        send(sender, "playerlist.footer");
    }
}

final class SeenCommand extends YnmCommand {

    SeenCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.seen", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.seen");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.isOnline() && target.getPlayer() != null) {
            var loc = target.getPlayer().getLocation();
            send(
                    sender,
                    "seen.online",
                    Placeholder.unparsed("player", String.valueOf(target.getName())),
                    Placeholder.unparsed("world", loc.getWorld().getName()),
                    Placeholder.unparsed("x", String.valueOf(loc.getBlockX())),
                    Placeholder.unparsed("y", String.valueOf(loc.getBlockY())),
                    Placeholder.unparsed("z", String.valueOf(loc.getBlockZ())));
            return;
        }
        services()
                .storage
                .playerProfiles()
                .find(target.getUniqueId())
                .thenAccept(
                        opt -> {
                            if (opt.isEmpty()) {
                                send(sender, "error.player_not_found");
                                return;
                            }
                            var profile = opt.get();
                            send(
                                    sender,
                                    "seen.offline",
                                    Placeholder.unparsed(
                                            "player", String.valueOf(target.getName())),
                                    Placeholder.unparsed(
                                            "last_seen",
                                            TimeParser.format(
                                                            System.currentTimeMillis()
                                                                    - profile.lastSeenAt())
                                                    + " ago"),
                                    Placeholder.unparsed(
                                            "first_join",
                                            java.time.Instant.ofEpochMilli(profile.firstJoinedAt())
                                                    .toString()),
                                    Placeholder.unparsed(
                                            "playtime",
                                            TimeParser.format(profile.playtimeSeconds() * 1000)));
                        });
    }
}

final class RulesCommand extends YnmCommand {

    RulesCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.rules", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            send(sender, "rules.reloaded");
            return;
        }
        List<String> rules = plugin.configManager().main().getStringList("rules");
        if (rules.isEmpty()) {
            send(sender, "rules.empty");
            return;
        }
        rules.forEach(rule -> sender.sendMessage(MiniMessage.miniMessage().deserialize(rule)));
    }
}

final class SuicideCommand extends YnmCommand {

    SuicideCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.suicide", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        player.setHealth(0);
        send(sender, "suicide.success");
    }
}

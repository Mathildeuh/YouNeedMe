package fr.mathildeuh.youneedme.modules.moderation;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.moderation.Punishment;
import fr.mathildeuh.youneedme.api.moderation.PunishmentType;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class BanCommand extends YnmCommand {

    BanCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.ban", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.ban");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            send(sender, "ban.cannot_ban_self");
            return;
        }
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.hasPermission("youneedme.exempt.ban")) {
            send(sender, "ban.exempt");
            return;
        }
        DurationReason parsed =
                DurationReason.parse(
                        java.util.Arrays.copyOfRange(args, 1, args.length),
                        plugin.lang()
                                .renderPlain(plugin.lang().defaultLocale(), "ban.default_reason"));
        UUID issuedBy = sender instanceof Player p ? p.getUniqueId() : null;
        services()
                .moderation
                .ban(target.getUniqueId(), issuedBy, parsed.reason(), parsed.durationMillis())
                .thenAccept(punishment -> onBanned(sender, target, punishment, onlineTarget))
                .exceptionally(reportAsyncFailure(sender, label));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }

    private void onBanned(
            CommandSender sender,
            OfflinePlayer target,
            Punishment punishment,
            Player onlineTarget) {
        String duration =
                punishment.isPermanent()
                        ? "permanent"
                        : TimeParser.format(punishment.expiresAt() - punishment.issuedAt());
        send(
                sender,
                "ban.success",
                Placeholder.unparsed("target", String.valueOf(target.getName())),
                Placeholder.unparsed("duration", duration));
        broadcast(
                punishment.isPermanent() ? "ban.broadcast" : "ban.broadcast_temp",
                Placeholder.unparsed("target", String.valueOf(target.getName())),
                Placeholder.unparsed("banner", senderName(sender)),
                Placeholder.unparsed("reason", punishment.reason()),
                Placeholder.unparsed("duration", duration));
        if (onlineTarget != null) {
            onlineTarget.kick(
                    plugin.lang()
                            .render(
                                    plugin.lang().resolveLocale(onlineTarget),
                                    "ban.screen_message",
                                    Placeholder.unparsed("player", onlineTarget.getName()),
                                    Placeholder.unparsed(
                                            "date", java.time.Instant.now().toString()),
                                    Placeholder.unparsed("id", String.valueOf(punishment.id())),
                                    Placeholder.unparsed("reason", punishment.reason())));
        }
    }
}

final class BanIpCommand extends YnmCommand {

    BanIpCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.banip", false);
    }

    private static final java.util.regex.Pattern IP_PATTERN =
            java.util.regex.Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.banip");
            return;
        }
        String ip;
        Player onlineTarget = null;
        String targetLabel = args[0];
        if (IP_PATTERN.matcher(args[0]).matches()) {
            ip = args[0];
        } else {
            onlineTarget = Bukkit.getPlayerExact(args[0]);
            if (onlineTarget == null || onlineTarget.getAddress() == null) {
                send(sender, "banip.no_stored_ip", Placeholder.unparsed("player", args[0]));
                return;
            }
            ip = onlineTarget.getAddress().getAddress().getHostAddress();
        }
        DurationReason parsed =
                DurationReason.parse(
                        java.util.Arrays.copyOfRange(args, 1, args.length),
                        plugin.lang()
                                .renderPlain(
                                        plugin.lang().defaultLocale(), "banip.default_reason"));
        UUID issuedBy = sender instanceof Player p ? p.getUniqueId() : null;
        String finalIp = ip;
        Player finalOnlineTarget = onlineTarget;
        services()
                .moderation
                .banIp(ip, issuedBy, parsed.reason(), parsed.durationMillis())
                .thenAccept(
                        punishment -> {
                            int kicked = 0;
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (online.getAddress() != null
                                        && finalIp.equals(
                                                online.getAddress()
                                                        .getAddress()
                                                        .getHostAddress())) {
                                    online.kick(
                                            plugin.lang()
                                                    .render(
                                                            plugin.lang().resolveLocale(online),
                                                            "ban.screen_message",
                                                            Placeholder.unparsed(
                                                                    "player", online.getName()),
                                                            Placeholder.unparsed(
                                                                    "date",
                                                                    java.time.Instant.now()
                                                                            .toString()),
                                                            Placeholder.unparsed(
                                                                    "id",
                                                                    String.valueOf(
                                                                            punishment.id())),
                                                            Placeholder.unparsed(
                                                                    "reason",
                                                                    punishment.reason())));
                                    kicked++;
                                }
                            }
                            send(
                                    sender,
                                    "banip.success",
                                    Placeholder.unparsed("ip", finalIp),
                                    Placeholder.unparsed("count", String.valueOf(kicked)));
                            String durationLabel =
                                    punishment.isPermanent()
                                            ? "permanent"
                                            : TimeParser.format(
                                                    punishment.expiresAt() - punishment.issuedAt());
                            broadcast(
                                    punishment.isPermanent()
                                            ? "banip.broadcast"
                                            : "banip.broadcast_temp",
                                    Placeholder.unparsed("target", targetLabel),
                                    Placeholder.unparsed("ip", finalIp),
                                    Placeholder.unparsed("banner", senderName(sender)),
                                    Placeholder.unparsed("reason", punishment.reason()),
                                    Placeholder.unparsed("duration", durationLabel));
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class UnbanCommand extends YnmCommand {

    UnbanCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.unban", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.unban");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID revokedBy = sender instanceof Player p ? p.getUniqueId() : null;
        services()
                .moderation
                .unban(target.getUniqueId(), revokedBy)
                .thenAccept(
                        unbanned -> {
                            if (!unbanned) {
                                send(
                                        sender,
                                        "unban.not_banned",
                                        Placeholder.unparsed(
                                                "player", String.valueOf(target.getName())));
                                return;
                            }
                            send(
                                    sender,
                                    "unban.success",
                                    Placeholder.unparsed(
                                            "target", String.valueOf(target.getName())));
                            broadcast(
                                    "unban.broadcast",
                                    Placeholder.unparsed(
                                            "target", String.valueOf(target.getName())),
                                    Placeholder.unparsed("unbanner", senderName(sender)));
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }
}

final class UnbanIpCommand extends YnmCommand {

    UnbanIpCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.unbanip", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.unbanip");
            return;
        }
        UUID revokedBy = sender instanceof Player p ? p.getUniqueId() : null;
        services()
                .moderation
                .unbanIp(args[0], revokedBy)
                .thenAccept(
                        unbanned -> {
                            if (!unbanned) {
                                send(
                                        sender,
                                        "unbanip.not_banned",
                                        Placeholder.unparsed("ip", args[0]));
                                return;
                            }
                            send(sender, "unbanip.success", Placeholder.unparsed("ip", args[0]));
                            broadcast(
                                    "unbanip.broadcast",
                                    Placeholder.unparsed("ip", args[0]),
                                    Placeholder.unparsed("unbanner", senderName(sender)));
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }
}

final class MuteCommand extends YnmCommand {

    MuteCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.mute", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.mute");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            send(sender, "mute.cannot_mute_self");
            return;
        }
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.hasPermission("youneedme.exempt.mute")) {
            send(sender, "mute.exempt");
            return;
        }
        DurationReason parsed =
                DurationReason.parse(
                        java.util.Arrays.copyOfRange(args, 1, args.length), "Breaking chat rules");
        UUID issuedBy = sender instanceof Player p ? p.getUniqueId() : null;
        services()
                .moderation
                .mute(target.getUniqueId(), issuedBy, parsed.reason(), parsed.durationMillis())
                .thenAccept(
                        punishment -> {
                            String duration =
                                    punishment.isPermanent()
                                            ? "permanent"
                                            : TimeParser.format(
                                                    punishment.expiresAt() - punishment.issuedAt());
                            send(
                                    sender,
                                    "mute.success",
                                    Placeholder.unparsed(
                                            "target", String.valueOf(target.getName())),
                                    Placeholder.unparsed("duration", duration));
                            broadcast(
                                    punishment.isPermanent()
                                            ? "mute.broadcast"
                                            : "mute.broadcast_temp",
                                    Placeholder.unparsed(
                                            "target", String.valueOf(target.getName())),
                                    Placeholder.unparsed("muter", senderName(sender)),
                                    Placeholder.unparsed("reason", punishment.reason()),
                                    Placeholder.unparsed("duration", duration));
                            if (onlineTarget != null) {
                                send(
                                        onlineTarget,
                                        "mute.target_message",
                                        Placeholder.unparsed("duration", duration),
                                        Placeholder.unparsed("muter", senderName(sender)),
                                        Placeholder.unparsed("reason", punishment.reason()));
                            }
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class UnmuteCommand extends YnmCommand {

    UnmuteCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.unmute", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.unmute");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID revokedBy = sender instanceof Player p ? p.getUniqueId() : null;
        services()
                .moderation
                .unmute(target.getUniqueId(), revokedBy)
                .thenAccept(
                        unmuted -> {
                            if (!unmuted) {
                                send(
                                        sender,
                                        "unmute.not_muted",
                                        Placeholder.unparsed(
                                                "player", String.valueOf(target.getName())));
                                return;
                            }
                            send(
                                    sender,
                                    "unmute.success",
                                    Placeholder.unparsed(
                                            "target", String.valueOf(target.getName())));
                            broadcast(
                                    "unmute.broadcast",
                                    Placeholder.unparsed(
                                            "target", String.valueOf(target.getName())),
                                    Placeholder.unparsed("unmuter", senderName(sender)));
                            Player online = target.getPlayer();
                            if (online != null) {
                                send(online, "unmute.target_message");
                            }
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class KickCommand extends YnmCommand {

    KickCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.kick", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.kick");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "error.player_not_online", Placeholder.unparsed("player", args[0]));
            return;
        }
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            send(sender, "kick.cannot_kick_self");
            return;
        }
        if (target.hasPermission("youneedme.exempt.kick")) {
            send(sender, "kick.exempt");
            return;
        }
        String reason =
                args.length > 1
                        ? String.join(" ", java.util.Arrays.asList(args).subList(1, args.length))
                        : "Kicked by an operator.";
        UUID issuedBy = sender instanceof Player p ? p.getUniqueId() : null;
        services()
                .moderation
                .kick(target.getUniqueId(), issuedBy, reason)
                .thenAccept(
                        punishment -> {
                            target.kick(
                                    plugin.lang()
                                            .render(
                                                    plugin.lang().resolveLocale(target),
                                                    "kick.screen_message",
                                                    Placeholder.unparsed("reason", reason),
                                                    Placeholder.unparsed(
                                                            "kicker", senderName(sender))));
                            send(
                                    sender,
                                    "kick.success",
                                    Placeholder.unparsed("target", target.getName()));
                            broadcast(
                                    "kick.broadcast",
                                    Placeholder.unparsed("target", target.getName()),
                                    Placeholder.unparsed("kicker", senderName(sender)),
                                    Placeholder.unparsed("reason", reason));
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class CheckPunishCommand extends YnmCommand {

    CheckPunishCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.checkpunish", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.checkpunish");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID id = target.getUniqueId();
        var moderation = services().moderation;
        moderation
                .activeBan(id)
                .thenAcceptBoth(
                        moderation.activeMute(id),
                        (ban, mute) -> {
                            send(sender, "checkpunish.header");
                            if (ban != null) {
                                send(
                                        sender,
                                        "checkpunish.ban.active",
                                        Placeholder.unparsed("reason", ban.reason()),
                                        Placeholder.unparsed(
                                                "banner", String.valueOf(ban.issuedBy())),
                                        Placeholder.unparsed(
                                                "time",
                                                TimeParser.format(
                                                        System.currentTimeMillis()
                                                                - ban.issuedAt())),
                                        Placeholder.unparsed(
                                                "expires",
                                                ban.isPermanent()
                                                        ? "never"
                                                        : TimeParser.format(
                                                                ban.expiresAt()
                                                                        - System
                                                                                .currentTimeMillis())));
                            } else {
                                send(sender, "checkpunish.ban.none");
                            }
                            if (mute != null) {
                                send(
                                        sender,
                                        "checkpunish.mute.active",
                                        Placeholder.unparsed("reason", mute.reason()),
                                        Placeholder.unparsed(
                                                "muter", String.valueOf(mute.issuedBy())),
                                        Placeholder.unparsed(
                                                "time",
                                                TimeParser.format(
                                                        System.currentTimeMillis()
                                                                - mute.issuedAt())),
                                        Placeholder.unparsed(
                                                "expires",
                                                mute.isPermanent()
                                                        ? "never"
                                                        : TimeParser.format(
                                                                mute.expiresAt()
                                                                        - System
                                                                                .currentTimeMillis())));
                            } else {
                                send(sender, "checkpunish.mute.none");
                            }
                            if (ban == null && mute == null) {
                                send(
                                        sender,
                                        "checkpunish.clean",
                                        Placeholder.unparsed(
                                                "player", String.valueOf(target.getName())));
                            }
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class BanListCommand extends YnmCommand {

    BanListCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.banlist", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String scope = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "players";
        int page = args.length > 1 ? parsePage(args[1]) : 1;
        PunishmentType type = "ips".equals(scope) ? PunishmentType.IP_BAN : PunishmentType.BAN;
        services()
                .moderation
                .activePunishments(type, page, 10)
                .thenAccept(
                        list -> {
                            if (list.isEmpty()) {
                                send(
                                        sender,
                                        "ips".equals(scope)
                                                ? "banlist.no_ip_bans"
                                                : "banlist.no_player_bans");
                                return;
                            }
                            send(
                                    sender,
                                    "ips".equals(scope)
                                            ? "banlist.ip.header"
                                            : "banlist.player.header",
                                    Placeholder.unparsed("count", String.valueOf(list.size())),
                                    Placeholder.unparsed("page", String.valueOf(page)),
                                    Placeholder.unparsed("total", String.valueOf(page)));
                            int index = (page - 1) * 10 + 1;
                            for (Punishment punishment : list) {
                                String who =
                                        punishment.type() == PunishmentType.IP_BAN
                                                ? punishment.targetIp()
                                                : Bukkit.getOfflinePlayer(punishment.target())
                                                        .getName();
                                if (punishment.isPermanent()) {
                                    send(
                                            sender,
                                            punishment.type() == PunishmentType.IP_BAN
                                                    ? "banlist.entry.ip.perm"
                                                    : "banlist.entry.player.perm",
                                            Placeholder.unparsed("index", String.valueOf(index++)),
                                            Placeholder.unparsed(
                                                    punishment.type() == PunishmentType.IP_BAN
                                                            ? "ip"
                                                            : "player",
                                                    String.valueOf(who)),
                                            Placeholder.unparsed("reason", punishment.reason()),
                                            Placeholder.unparsed(
                                                    "time",
                                                    TimeParser.format(
                                                            System.currentTimeMillis()
                                                                    - punishment.issuedAt())));
                                } else {
                                    send(
                                            sender,
                                            punishment.type() == PunishmentType.IP_BAN
                                                    ? "banlist.entry.ip.temp"
                                                    : "banlist.entry.player.temp",
                                            Placeholder.unparsed("index", String.valueOf(index++)),
                                            Placeholder.unparsed(
                                                    punishment.type() == PunishmentType.IP_BAN
                                                            ? "ip"
                                                            : "player",
                                                    String.valueOf(who)),
                                            Placeholder.unparsed("reason", punishment.reason()),
                                            Placeholder.unparsed(
                                                    "expires",
                                                    TimeParser.format(
                                                            punishment.expiresAt()
                                                                    - System.currentTimeMillis())));
                                }
                            }
                            send(
                                    sender,
                                    "banlist.footer",
                                    Placeholder.unparsed("current", String.valueOf(page)),
                                    Placeholder.unparsed("total", String.valueOf(page)));
                        })
                .exceptionally(reportAsyncFailure(sender, label));
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("players", "ips") : List.of();
    }
}

final class SmiteCommand extends YnmCommand {

    SmiteCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.smite", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.smite");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "error.player_not_online", Placeholder.unparsed("player", args[0]));
            return;
        }
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            send(sender, "smite.cannot_smite_self");
            return;
        }
        plugin.scheduler()
                .runAtLocation(
                        target.getLocation(),
                        () -> target.getWorld().strikeLightning(target.getLocation()));
        send(sender, "smite.success", Placeholder.unparsed("target", target.getName()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class GodCommand extends YnmCommand {

    GodCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.god", false);
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
        boolean nowGod = !services().godMode.contains(target.getUniqueId());
        if (nowGod) {
            services().godMode.add(target.getUniqueId());
        } else {
            services().godMode.remove(target.getUniqueId());
        }
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        if (self) {
            send(sender, nowGod ? "god.enabled" : "god.disabled");
        } else {
            send(
                    sender,
                    nowGod ? "god.enabled.other" : "god.disabled.other",
                    Placeholder.unparsed("target", target.getName()));
            send(
                    target,
                    nowGod ? "god.enabled.by" : "god.disabled.by",
                    Placeholder.unparsed("player", senderName(sender)));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class VanishCommand extends YnmCommand {

    VanishCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.vanish", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        boolean nowVanished = !services().vanished.contains(player.getUniqueId());
        if (nowVanished) {
            services().vanished.add(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission("youneedme.vanish.see")) {
                    other.hidePlayer(plugin, player);
                }
            }
        } else {
            services().vanished.remove(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, player);
            }
        }
        send(sender, nowVanished ? "vanish.enabled" : "vanish.disabled");
    }
}

final class InvSeeCommand extends YnmCommand {

    InvSeeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.invsee", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player viewer = player(sender);
        if (args.length < 1) {
            send(sender, "command.usage.invsee");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "error.player_not_online", Placeholder.unparsed("player", args[0]));
            return;
        }
        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            send(sender, "invsee.self");
            return;
        }
        viewer.openInventory(target.getInventory());
        send(sender, "invsee.opened", Placeholder.unparsed("target", target.getName()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class EnderSeeCommand extends YnmCommand {

    EnderSeeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.endersee", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player viewer = player(sender);
        if (args.length < 1) {
            send(sender, "command.usage.endersee");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "error.player_not_online", Placeholder.unparsed("player", args[0]));
            return;
        }
        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            send(sender, "endersee.self");
            return;
        }
        viewer.openInventory(target.getEnderChest());
        send(sender, "endersee.opened", Placeholder.unparsed("target", target.getName()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class SudoCommand extends YnmCommand {

    SudoCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.sudo", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            send(sender, "command.usage.sudo");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "error.player_not_online", Placeholder.unparsed("player", args[0]));
            return;
        }
        if (target.hasPermission("youneedme.exempt.sudo")) {
            send(sender, "sudo.exempt");
            return;
        }
        String action = String.join(" ", java.util.Arrays.asList(args).subList(1, args.length));
        if (action.startsWith("/")) {
            target.performCommand(action.substring(1));
            send(
                    sender,
                    "sudo.success.command",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("command", action));
        } else {
            target.chat(action);
            send(
                    sender,
                    "sudo.success.chat",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("command", action));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class ClearInventoryCommand extends YnmCommand {

    ClearInventoryCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.clearinventory", false);
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
        target.getInventory().clear();
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        if (self) {
            send(sender, "clearinventory.success");
        } else {
            send(
                    sender,
                    "clearinventory.success.other",
                    Placeholder.unparsed("target", target.getName()));
            send(
                    target,
                    "clearinventory.success.by",
                    Placeholder.unparsed("player", senderName(sender)));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

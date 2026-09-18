package fr.mathildeuh.youneedme.modules.warps;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.event.WarpUseEvent;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.model.Warp;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class WarpCommand extends YnmCommand {

    WarpCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.warp", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.warp");
            return;
        }
        String name = args[0];
        services()
                .warps
                .get(name)
                .thenAccept(
                        warp -> {
                            if (warp == null) {
                                send(sender, "warp.not_found", Placeholder.unparsed("warp", name));
                                return;
                            }
                            if (warp.permission() != null
                                    && !player.hasPermission(warp.permission())) {
                                send(sender, "warp.no_permission_specific");
                                return;
                            }
                            if (services().cooldowns.isOnCooldown(player.getUniqueId(), "warp")) {
                                send(
                                        sender,
                                        "warp.cooldown",
                                        Placeholder.unparsed(
                                                "time",
                                                String.valueOf(
                                                        services()
                                                                        .cooldowns
                                                                        .remainingMillis(
                                                                                player
                                                                                        .getUniqueId(),
                                                                                "warp")
                                                                / 1000)));
                                return;
                            }
                            if (warp.cost() > 0) {
                                services()
                                        .economy
                                        .has(player.getUniqueId(), "default", warp.cost())
                                        .thenAccept(
                                                has -> {
                                                    if (!has) {
                                                        send(
                                                                sender,
                                                                "warp.insufficient_funds",
                                                                Placeholder.unparsed(
                                                                        "cost",
                                                                        services()
                                                                                .economy
                                                                                .currency("default")
                                                                                .format(
                                                                                        warp
                                                                                                .cost())));
                                                        return;
                                                    }
                                                    beginWarp(player, warp);
                                                });
                                return;
                            }
                            beginWarp(player, warp);
                        });
    }

    private void beginWarp(Player player, Warp warp) {
        WarpUseEvent event = new WarpUseEvent(player, warp);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        int warmupSeconds =
                plugin.configManager().module("warps").getInt("teleport-warmup-seconds", 3);
        if (warmupSeconds > 0) {
            send(
                    player,
                    "warp.warmup",
                    Placeholder.unparsed("warp", warp.name()),
                    Placeholder.unparsed("time", String.valueOf(warmupSeconds)));
        }
        services()
                .warmups
                .start(
                        player,
                        warmupSeconds,
                        plugin.scheduler(),
                        () -> doTeleport(player, warp),
                        () -> send(player, "warp.cancelled_movement"));
    }

    private void doTeleport(Player player, Warp warp) {
        Location location = warp.position().toLocation();
        if (location == null) {
            return;
        }
        if (warp.cost() > 0) {
            services()
                    .economy
                    .withdraw(player.getUniqueId(), warp.cost())
                    .thenAccept(
                            result -> {
                                if (!result.isSuccess()) {
                                    return;
                                }
                                send(
                                        player,
                                        "warp.charged",
                                        Placeholder.unparsed(
                                                "amount",
                                                services()
                                                        .economy
                                                        .currency("default")
                                                        .format(warp.cost())));
                                completeTeleport(player, warp, location);
                            });
        } else {
            completeTeleport(player, warp, location);
        }
    }

    private void completeTeleport(Player player, Warp warp, Location location) {
        plugin.scheduler()
                .runAtLocation(
                        location,
                        () -> {
                            player.teleportAsync(location);
                            services()
                                    .cooldowns
                                    .set(
                                            player.getUniqueId(),
                                            "warp",
                                            plugin.configManager()
                                                            .module("warps")
                                                            .getLong("cooldown-seconds", 0)
                                                    * 1000L);
                            send(player, "warp.success", Placeholder.unparsed("warp", warp.name()));
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return services().warps.list().getNow(List.of()).stream().map(Warp::name).toList();
        }
        return List.of();
    }
}

final class SetWarpCommand extends YnmCommand {

    SetWarpCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.setwarp", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.setwarp");
            return;
        }
        Player player = player(sender);
        String name = args[0];
        services()
                .warps
                .get(name)
                .thenAccept(
                        existing -> {
                            if (existing != null) {
                                send(
                                        sender,
                                        "warp.already_exists",
                                        Placeholder.unparsed("warp", name));
                                return;
                            }
                            services()
                                    .warps
                                    .create(
                                            name,
                                            Position.of(player.getLocation()),
                                            player.getUniqueId())
                                    .thenAccept(
                                            warp ->
                                                    send(
                                                            sender,
                                                            "warp.created",
                                                            Placeholder.unparsed("warp", name),
                                                            Placeholder.unparsed(
                                                                    "world",
                                                                    warp.position().worldName()),
                                                            Placeholder.unparsed(
                                                                    "x",
                                                                    String.valueOf(
                                                                            (int)
                                                                                    warp.position()
                                                                                            .x())),
                                                            Placeholder.unparsed(
                                                                    "y",
                                                                    String.valueOf(
                                                                            (int)
                                                                                    warp.position()
                                                                                            .y())),
                                                            Placeholder.unparsed(
                                                                    "z",
                                                                    String.valueOf(
                                                                            (int)
                                                                                    warp.position()
                                                                                            .z()))));
                        });
    }
}

final class DelWarpCommand extends YnmCommand {

    DelWarpCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.delwarp", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.delwarp");
            return;
        }
        services()
                .warps
                .delete(args[0])
                .thenAccept(
                        deleted -> {
                            if (deleted) {
                                send(sender, "warp.deleted", Placeholder.unparsed("warp", args[0]));
                            } else {
                                send(
                                        sender,
                                        "warp.not_found",
                                        Placeholder.unparsed("warp", args[0]));
                            }
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return services().warps.list().getNow(List.of()).stream().map(Warp::name).toList();
        }
        return List.of();
    }
}

final class WarpsCommand extends YnmCommand {

    WarpsCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.warps", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        services()
                .warps
                .listVisibleTo(player.getUniqueId())
                .thenAccept(
                        warps -> {
                            if (warps.isEmpty()) {
                                send(sender, "warp.no_warps");
                                return;
                            }
                            send(sender, "warp.list_header");
                            warps.stream()
                                    .collect(
                                            java.util.stream.Collectors.groupingBy(
                                                    w -> w.category() == null ? "" : w.category()))
                                    .forEach(
                                            (category, list) -> {
                                                if (!category.isEmpty()) {
                                                    send(
                                                            sender,
                                                            "warp.category_header",
                                                            Placeholder.unparsed(
                                                                    "category", category));
                                                }
                                                list.forEach(
                                                        w ->
                                                                send(
                                                                        sender,
                                                                        "warp.entry",
                                                                        Placeholder.unparsed(
                                                                                "warp", w.name()),
                                                                        Placeholder.unparsed(
                                                                                "cost",
                                                                                w.cost() > 0
                                                                                        ? services()
                                                                                                .economy
                                                                                                .currency(
                                                                                                        "default")
                                                                                                .format(
                                                                                                        w
                                                                                                                .cost())
                                                                                        : "Free"),
                                                                        Placeholder.unparsed(
                                                                                "description",
                                                                                w.description()
                                                                                                        == null
                                                                                                || w.description()
                                                                                                        .isBlank()
                                                                                        ? ""
                                                                                        : w
                                                                                                .description())));
                                            });
                        });
    }
}

final class WarpAdminCommand extends YnmCommand {

    WarpAdminCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.warpadmin", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.warpadmin");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(sub)) {
            services().warps.reload().thenRun(() -> send(sender, "warpadmin.reloaded"));
            return;
        }
        if (args.length < 2) {
            send(sender, "command.usage.warpadmin");
            return;
        }
        String name = args[1];
        services()
                .warps
                .get(name)
                .thenAccept(
                        warp -> {
                            if (warp == null) {
                                send(sender, "warp.not_found", Placeholder.unparsed("warp", name));
                                return;
                            }
                            switch (sub) {
                                case "setperm" -> {
                                    String perm = args.length > 2 ? args[2] : null;
                                    services()
                                            .warps
                                            .update(
                                                    warp.withPermission(
                                                            "none".equalsIgnoreCase(perm)
                                                                    ? null
                                                                    : perm))
                                            .thenAccept(
                                                    w ->
                                                            send(
                                                                    sender,
                                                                    perm == null
                                                                                    || "none"
                                                                                            .equalsIgnoreCase(
                                                                                                    perm)
                                                                            ? "warpadmin.perm_removed"
                                                                            : "warpadmin.perm_set",
                                                                    Placeholder.unparsed(
                                                                            "warp", name),
                                                                    Placeholder.unparsed(
                                                                            "perm",
                                                                            String.valueOf(perm))));
                                }
                                case "setcost" -> {
                                    double cost = args.length > 2 ? parseDouble(args[2]) : 0;
                                    services()
                                            .warps
                                            .update(warp.withCost(cost))
                                            .thenAccept(
                                                    w ->
                                                            send(
                                                                    sender,
                                                                    "warpadmin.cost_set",
                                                                    Placeholder.unparsed(
                                                                            "warp", name),
                                                                    Placeholder.unparsed(
                                                                            "cost",
                                                                            String.valueOf(cost))));
                                }
                                case "setdesc" -> {
                                    String desc =
                                            args.length > 2
                                                    ? String.join(
                                                            " ",
                                                            java.util.Arrays.asList(args)
                                                                    .subList(2, args.length))
                                                    : "";
                                    services()
                                            .warps
                                            .update(warp.withDescription(desc))
                                            .thenAccept(
                                                    w ->
                                                            send(
                                                                    sender,
                                                                    "warpadmin.desc_set",
                                                                    Placeholder.unparsed(
                                                                            "warp", name)));
                                }
                                case "setcategory" -> {
                                    String category = args.length > 2 ? args[2] : null;
                                    services()
                                            .warps
                                            .update(warp.withCategory(category))
                                            .thenAccept(
                                                    w ->
                                                            send(
                                                                    sender,
                                                                    "warpadmin.category_set",
                                                                    Placeholder.unparsed(
                                                                            "warp", name),
                                                                    Placeholder.unparsed(
                                                                            "category",
                                                                            String.valueOf(
                                                                                    category))));
                                }
                                case "hide" ->
                                        services()
                                                .warps
                                                .update(warp.withHidden(true))
                                                .thenAccept(
                                                        w ->
                                                                send(
                                                                        sender,
                                                                        "warpadmin.hidden",
                                                                        Placeholder.unparsed(
                                                                                "warp", name)));
                                case "unhide" ->
                                        services()
                                                .warps
                                                .update(warp.withHidden(false))
                                                .thenAccept(
                                                        w ->
                                                                send(
                                                                        sender,
                                                                        "warpadmin.unhidden",
                                                                        Placeholder.unparsed(
                                                                                "warp", name)));
                                case "move" -> {
                                    Player player = player(sender);
                                    services()
                                            .warps
                                            .update(
                                                    warp.withPosition(
                                                            Position.of(player.getLocation())))
                                            .thenAccept(
                                                    w ->
                                                            send(
                                                                    sender,
                                                                    "warpadmin.moved",
                                                                    Placeholder.unparsed(
                                                                            "warp", name)));
                                }
                                case "info" ->
                                        send(
                                                sender,
                                                "warpadmin.info",
                                                Placeholder.unparsed("warp", warp.name()),
                                                Placeholder.unparsed(
                                                        "world", warp.position().worldName()),
                                                Placeholder.unparsed(
                                                        "x",
                                                        String.valueOf((int) warp.position().x())),
                                                Placeholder.unparsed(
                                                        "y",
                                                        String.valueOf((int) warp.position().y())),
                                                Placeholder.unparsed(
                                                        "z",
                                                        String.valueOf((int) warp.position().z())),
                                                Placeholder.unparsed(
                                                        "category",
                                                        String.valueOf(warp.category())),
                                                Placeholder.unparsed(
                                                        "cost", String.valueOf(warp.cost())),
                                                Placeholder.unparsed(
                                                        "permission",
                                                        String.valueOf(warp.permission())),
                                                Placeholder.unparsed(
                                                        "hidden", String.valueOf(warp.hidden())),
                                                Placeholder.unparsed(
                                                        "description",
                                                        String.valueOf(warp.description())));
                                default -> send(sender, "command.usage.warpadmin");
                            }
                        });
    }

    private static double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of(
                    "setperm",
                    "setcost",
                    "setdesc",
                    "setcategory",
                    "hide",
                    "unhide",
                    "move",
                    "info",
                    "reload");
        }
        if (args.length == 2) {
            return services().warps.list().getNow(List.of()).stream().map(Warp::name).toList();
        }
        return List.of();
    }
}

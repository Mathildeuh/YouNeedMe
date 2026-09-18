package fr.mathildeuh.youneedme.modules.homes;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.homes.HomeService;
import fr.mathildeuh.youneedme.api.model.Home;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class SetHomeCommand extends YnmCommand {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    SetHomeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.sethome", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        String name = args.length > 0 ? args[0] : "home";
        if (!VALID_NAME.matcher(name).matches()) {
            send(sender, "home.set.invalid_name");
            return;
        }
        services()
                .homes
                .set(player.getUniqueId(), name, Position.of(player.getLocation()))
                .thenAccept(
                        home ->
                                send(
                                        sender,
                                        "home.set.success",
                                        Placeholder.unparsed("name", name)))
                .exceptionally(
                        t -> {
                            if (unwrap(t) instanceof HomeService.HomeLimitExceededException ex) {
                                send(
                                        sender,
                                        "home.set.limit_reached",
                                        Placeholder.unparsed("limit", String.valueOf(ex.limit)));
                            } else {
                                send(sender, "home.set.failed", Placeholder.unparsed("name", name));
                            }
                            return null;
                        });
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof java.util.concurrent.CompletionException && t.getCause() != null
                ? t.getCause()
                : t;
    }
}

final class DelHomeCommand extends YnmCommand {

    DelHomeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.delhome", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "home.delete.no_name_provided");
            return;
        }
        String name = args[0];
        String pending = services().pendingHomeDeleteConfirm.get(player.getUniqueId());
        if (!name.equalsIgnoreCase(pending)) {
            services().pendingHomeDeleteConfirm.put(player.getUniqueId(), name);
            send(sender, "home.delete.confirm", Placeholder.unparsed("name", name));
            return;
        }
        services().pendingHomeDeleteConfirm.remove(player.getUniqueId());
        services()
                .homes
                .delete(player.getUniqueId(), name)
                .thenAccept(
                        deleted -> {
                            if (deleted) {
                                send(
                                        sender,
                                        "home.delete.success",
                                        Placeholder.unparsed("name", name));
                            } else {
                                send(
                                        sender,
                                        "home.delete.not_found",
                                        Placeholder.unparsed("name", name));
                            }
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return services().homes.list(player.getUniqueId()).getNow(List.of()).stream()
                    .map(Home::name)
                    .toList();
        }
        return List.of();
    }
}

final class HomeCommand extends YnmCommand {

    HomeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.home", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        String name = args.length > 0 ? args[0] : "home";
        if (services().warmups.hasPending(player.getUniqueId())) {
            send(sender, "home.teleport.already_pending");
            return;
        }
        long cooldownKey = 0;
        if (services().cooldowns.isOnCooldown(player.getUniqueId(), "home")) {
            send(
                    sender,
                    "home.teleport.cooldown",
                    Placeholder.unparsed(
                            "seconds",
                            String.valueOf(
                                    services()
                                                    .cooldowns
                                                    .remainingMillis(player.getUniqueId(), "home")
                                            / 1000)));
            return;
        }
        services()
                .homes
                .get(player.getUniqueId(), name)
                .thenAccept(
                        home -> {
                            if (home == null) {
                                send(
                                        sender,
                                        "home.teleport.not_found",
                                        Placeholder.unparsed("name", name));
                                return;
                            }
                            if (!home.position().isWorldLoaded()) {
                                send(
                                        sender,
                                        "home.teleport.invalid_world",
                                        Placeholder.unparsed("world", home.position().worldName()));
                                return;
                            }
                            int warmupSeconds =
                                    plugin.configManager()
                                            .module("homes")
                                            .getInt("teleport-warmup-seconds", 3);
                            if (warmupSeconds > 0) {
                                send(
                                        sender,
                                        "home.teleport.pending",
                                        Placeholder.unparsed("name", name),
                                        Placeholder.unparsed(
                                                "seconds", String.valueOf(warmupSeconds)));
                            }
                            services()
                                    .warmups
                                    .start(
                                            player,
                                            warmupSeconds,
                                            plugin.scheduler(),
                                            () -> teleport(player, home),
                                            () -> send(sender, "home.teleport.cancelled"));
                        });
    }

    private void teleport(Player player, Home home) {
        Location location = home.position().toLocation();
        if (location == null) {
            return;
        }
        plugin.scheduler()
                .runAtLocation(
                        location,
                        () -> {
                            player.teleportAsync(location);
                            services()
                                    .cooldowns
                                    .set(
                                            player.getUniqueId(),
                                            "home",
                                            plugin.configManager()
                                                            .module("homes")
                                                            .getLong("teleport-cooldown-seconds", 0)
                                                    * 1000L);
                            send(player, "home.teleport.success");
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return services().homes.list(player.getUniqueId()).getNow(List.of()).stream()
                    .map(Home::name)
                    .toList();
        }
        return List.of();
    }
}

final class HomesCommand extends YnmCommand {

    HomesCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.homes", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        services()
                .homes
                .list(player.getUniqueId())
                .thenAccept(
                        homes -> {
                            int limit = services().homes.limitFor(player.getUniqueId());
                            if (homes.isEmpty()) {
                                send(sender, "home.list.empty");
                                return;
                            }
                            send(
                                    sender,
                                    "homes.list.header",
                                    Placeholder.unparsed("used", String.valueOf(homes.size())),
                                    Placeholder.unparsed(
                                            "limit",
                                            limit == Integer.MAX_VALUE
                                                    ? "∞"
                                                    : String.valueOf(limit)));
                            send(sender, "homes.list.separator");
                            for (Home home : homes) {
                                send(
                                        sender,
                                        "homes.list.entry",
                                        Placeholder.unparsed("name", home.name()),
                                        Placeholder.unparsed("world", home.position().worldName()),
                                        Placeholder.unparsed(
                                                "x", String.valueOf((int) home.position().x())),
                                        Placeholder.unparsed(
                                                "y", String.valueOf((int) home.position().y())),
                                        Placeholder.unparsed(
                                                "z", String.valueOf((int) home.position().z())));
                            }
                            send(sender, "homes.list.footer");
                        });
    }
}

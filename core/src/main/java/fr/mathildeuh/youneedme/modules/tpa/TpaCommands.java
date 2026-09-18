package fr.mathildeuh.youneedme.modules.tpa;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class TpaCommand extends YnmCommand {

    TpaCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpa", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        sendRequest(sender, args, TpaModule.Type.TPA);
    }

    void sendRequest(CommandSender sender, String[] args, TpaModule.Type type) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.tpa");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "tpa.error.player_offline");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "tpa.error.self");
            return;
        }
        TpaModule tpa = services().tpa;
        if (tpa.isToggledOff(target.getUniqueId())) {
            send(
                    sender,
                    "tpa.error.target_blocked",
                    Placeholder.unparsed("player", target.getName()));
            return;
        }
        if (tpa.isIgnoring(target.getUniqueId(), player.getUniqueId())) {
            send(sender, "tpa.error.ignored", Placeholder.unparsed("player", target.getName()));
            return;
        }
        int seconds = plugin.configManager().module("tpa").getInt("request-expiry-seconds", 60);
        tpa.addRequest(
                new TpaModule.Request(
                        player.getUniqueId(),
                        target.getUniqueId(),
                        type,
                        System.currentTimeMillis() + seconds * 1000L));
        send(
                sender,
                type == TpaModule.Type.TPA ? "tpa.sent.tpa" : "tpa.sent.tpahere",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("seconds", String.valueOf(seconds)));
        send(
                target,
                type == TpaModule.Type.TPA ? "tpa.received.tpa" : "tpa.received.tpahere",
                Placeholder.unparsed("player", player.getName()));
    }
}

final class TpaHereCommand extends YnmCommand {

    private final TpaCommand delegate;

    TpaHereCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpahere", true);
        this.delegate = new TpaCommand(plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        delegate.sendRequest(sender, args, TpaModule.Type.TPAHERE);
    }
}

final class TpAcceptCommand extends YnmCommand {

    TpAcceptCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpaccept", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        TpaModule tpa = services().tpa;
        var request =
                args.length > 0
                        ? findFrom(tpa, player, args[0])
                        : tpa.incoming(player.getUniqueId());
        if (request.isEmpty()) {
            send(sender, "tpa.error.no_requests");
            return;
        }
        Player requester = Bukkit.getPlayer(request.get().from());
        tpa.clearIncoming(player.getUniqueId());
        if (requester == null) {
            send(sender, "tpa.error.player_offline");
            return;
        }
        send(sender, "tpa.accept.target", Placeholder.unparsed("player", requester.getName()));
        send(requester, "tpa.accept.requester", Placeholder.unparsed("player", player.getName()));
        Player toTeleport = request.get().type() == TpaModule.Type.TPA ? requester : player;
        Player destination = request.get().type() == TpaModule.Type.TPA ? player : requester;
        plugin.scheduler()
                .runAtLocation(
                        destination.getLocation(),
                        () -> toTeleport.teleportAsync(destination.getLocation()));
    }

    private static java.util.Optional<TpaModule.Request> findFrom(
            TpaModule tpa, Player target, String fromName) {
        Player from = Bukkit.getPlayerExact(fromName);
        if (from == null) {
            return java.util.Optional.empty();
        }
        return tpa.incoming(target.getUniqueId()).filter(r -> r.from().equals(from.getUniqueId()));
    }
}

final class TpDenyCommand extends YnmCommand {

    TpDenyCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpdeny", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        var request = services().tpa.incoming(player.getUniqueId());
        if (request.isEmpty()) {
            send(sender, "tpa.error.no_requests");
            return;
        }
        services().tpa.clearIncoming(player.getUniqueId());
        Player requester = Bukkit.getPlayer(request.get().from());
        send(
                sender,
                "tpa.deny.target",
                Placeholder.unparsed("player", requester == null ? "?" : requester.getName()));
        if (requester != null) {
            send(requester, "tpa.deny.requester", Placeholder.unparsed("player", player.getName()));
        }
    }
}

final class TpCancelCommand extends YnmCommand {

    TpCancelCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpcancel", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.tpcancel");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "tpa.error.player_offline");
            return;
        }
        services().tpa.cancelOutgoing(player.getUniqueId(), target.getUniqueId());
        send(sender, "tpa.cancel.success", Placeholder.unparsed("player", target.getName()));
    }
}

final class TpaIgnoreCommand extends YnmCommand {

    TpaIgnoreCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpaignore", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.tpaignore");
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "tpa.error.ignore_self");
            return;
        }
        boolean nowIgnoring =
                services().tpa.toggleIgnore(player.getUniqueId(), target.getUniqueId());
        send(
                sender,
                nowIgnoring ? "tpa.ignore.added" : "tpa.ignore.removed",
                Placeholder.unparsed("player", String.valueOf(target.getName())));
    }
}

final class TpaQueueCommand extends YnmCommand {

    TpaQueueCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpaqueue", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        TpaModule tpa = services().tpa;
        var incoming = tpa.incomingAll(player.getUniqueId());
        var outgoing = tpa.outgoing(player.getUniqueId());
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            send(sender, "tpa.queue.empty");
            return;
        }
        if (!incoming.isEmpty()) {
            send(sender, "tpa.queue.header_incoming");
            incoming.forEach(r -> printEntry(sender, r, r.from()));
        }
        if (!outgoing.isEmpty()) {
            send(sender, "tpa.queue.header_outgoing");
            outgoing.forEach(r -> printEntry(sender, r, r.to()));
        }
    }

    private void printEntry(CommandSender sender, TpaModule.Request request, java.util.UUID other) {
        var offline = Bukkit.getOfflinePlayer(other);
        long seconds = Math.max(0, (request.expiresAt() - System.currentTimeMillis()) / 1000);
        send(
                sender,
                "tpa.queue.entry",
                Placeholder.unparsed("type", request.type().name()),
                Placeholder.unparsed("player", String.valueOf(offline.getName())),
                Placeholder.unparsed("seconds", String.valueOf(seconds)));
    }
}

final class TpaToggleCommand extends YnmCommand {

    TpaToggleCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpatoggle", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        boolean nowOff = !services().tpa.isToggledOff(player.getUniqueId());
        services().tpa.setToggledOff(player.getUniqueId(), nowOff);
        send(sender, nowOff ? "tpa.toggle.disabled" : "tpa.toggle.enabled");
    }
}

final class TpHereCommand extends YnmCommand {

    TpHereCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tphere", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.tpa");
            return;
        }
        Player player = player(sender);
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "tpa.error.player_offline");
            return;
        }
        plugin.scheduler()
                .runAtLocation(
                        player.getLocation(), () -> target.teleportAsync(player.getLocation()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class TpHereAllCommand extends YnmCommand {

    TpHereAllCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tphereall", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                plugin.scheduler()
                        .runAtLocation(
                                player.getLocation(),
                                () -> online.teleportAsync(player.getLocation()));
            }
        }
    }
}

final class TpOfflineCommand extends YnmCommand {

    TpOfflineCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tpoffline", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.tpa");
            return;
        }
        Player player = player(sender);
        services()
                .storage
                .playerProfiles()
                .findUuidByUsername(args[0])
                .thenCompose(
                        uuid ->
                                uuid.isEmpty()
                                        ? java.util.concurrent.CompletableFuture.completedFuture(
                                                java.util.Optional
                                                        .<fr.mathildeuh.youneedme.api.model
                                                                        .PlayerProfile>
                                                                empty())
                                        : services().storage.playerProfiles().find(uuid.get()))
                .thenAccept(
                        profileOpt -> {
                            if (profileOpt.isEmpty() || profileOpt.get().lastLocation() == null) {
                                send(player, "error.player_not_found");
                                return;
                            }
                            org.bukkit.Location location =
                                    profileOpt.get().lastLocation().toLocation();
                            if (location != null) {
                                plugin.scheduler()
                                        .runAtLocation(
                                                location, () -> player.teleportAsync(location));
                            }
                        });
    }
}

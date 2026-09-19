package fr.mathildeuh.youneedme.modules.trade;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class TradeCommand extends YnmCommand {

    private final TradeGui gui;

    TradeCommand(YouNeedMe plugin, TradeGui gui) {
        super(plugin, "youneedme.trade", true);
        this.gui = gui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (!plugin.configManager().module("trade").getBoolean("enabled", true)) {
            send(sender, "trade.disabled");
            return;
        }
        if (args.length == 0) {
            send(sender, "command.usage.trade");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "trade.error.player_offline");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "trade.error.self");
            return;
        }
        TradeManager trades = services().trade;
        if (trades.isTrading(player.getUniqueId()) || trades.isTrading(target.getUniqueId())) {
            send(sender, "trade.error.already_trading");
            return;
        }
        var incomingFromTarget = trades.incoming(player.getUniqueId());
        if (incomingFromTarget.isPresent()
                && incomingFromTarget.get().from().equals(target.getUniqueId())) {
            // Both sent each other a request - accept immediately instead of leaving them stuck.
            trades.clearIncoming(player.getUniqueId());
            gui.open(player, target);
            return;
        }
        int seconds = plugin.configManager().module("trade").getInt("request-expiry-seconds", 60);
        trades.addRequest(
                new TradeManager.Request(
                        player.getUniqueId(),
                        target.getUniqueId(),
                        System.currentTimeMillis() + seconds * 1000L));
        send(
                sender,
                "trade.sent",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("seconds", String.valueOf(seconds)));
        send(target, "trade.received", Placeholder.unparsed("player", player.getName()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class TradeAcceptCommand extends YnmCommand {

    private final TradeGui gui;

    TradeAcceptCommand(YouNeedMe plugin, TradeGui gui) {
        super(plugin, "youneedme.trade", true);
        this.gui = gui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        TradeManager trades = services().trade;
        var request = trades.incoming(player.getUniqueId());
        if (request.isEmpty()) {
            send(sender, "trade.error.no_requests");
            return;
        }
        trades.clearIncoming(player.getUniqueId());
        Player requester = Bukkit.getPlayer(request.get().from());
        if (requester == null) {
            send(sender, "trade.error.player_offline");
            return;
        }
        if (trades.isTrading(player.getUniqueId()) || trades.isTrading(requester.getUniqueId())) {
            send(sender, "trade.error.already_trading");
            return;
        }
        gui.open(requester, player);
    }
}

final class TradeDenyCommand extends YnmCommand {

    TradeDenyCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.trade", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        TradeManager trades = services().trade;
        var request = trades.incoming(player.getUniqueId());
        if (request.isEmpty()) {
            send(sender, "trade.error.no_requests");
            return;
        }
        trades.clearIncoming(player.getUniqueId());
        send(sender, "trade.denied.target");
        Player requester = Bukkit.getPlayer(request.get().from());
        if (requester != null) {
            send(
                    requester,
                    "trade.denied.requester",
                    Placeholder.unparsed("player", player.getName()));
        }
    }
}

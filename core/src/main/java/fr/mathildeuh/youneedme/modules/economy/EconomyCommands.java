package fr.mathildeuh.youneedme.modules.economy;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.economy.EconomyResult;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class BalanceCommand extends YnmCommand {

    BalanceCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.balance", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        EconomyService economy = services().economy;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "balance.console");
                return;
            }
            economy.balance(player.getUniqueId())
                    .thenAccept(
                            balance ->
                                    send(
                                            sender,
                                            "balance.self",
                                            Placeholder.unparsed(
                                                    "balance",
                                                    economy.currency("default").format(balance))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        economy.balance(target.getUniqueId())
                .thenAccept(
                        balance ->
                                send(
                                        sender,
                                        "balance.other",
                                        Placeholder.unparsed(
                                                "player", String.valueOf(target.getName())),
                                        Placeholder.unparsed(
                                                "balance",
                                                economy.currency("default").format(balance))));
    }
}

final class BalTopCommand extends YnmCommand {

    BalTopCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.baltop", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        int page = args.length > 0 ? parsePage(args[0]) : 1;
        send(sender, "baltop.loading");
        services()
                .economy
                .top("default", page, 10)
                .thenAccept(
                        entries -> {
                            if (entries.isEmpty()) {
                                send(sender, "baltop.empty");
                                return;
                            }
                            send(
                                    sender,
                                    "baltop.header",
                                    Placeholder.unparsed("page", String.valueOf(page)));
                            int rank = (page - 1) * 10 + 1;
                            for (var entry : entries) {
                                send(
                                        sender,
                                        "baltop.entry",
                                        Placeholder.unparsed("rank", String.valueOf(rank++)),
                                        Placeholder.unparsed("player", entry.lastKnownUsername()),
                                        Placeholder.unparsed(
                                                "balance",
                                                services()
                                                        .economy
                                                        .currency("default")
                                                        .format(entry.balance())));
                            }
                            send(sender, "baltop.footer");
                            send(sender, "baltop.next");
                        });
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

final class PayCommand extends YnmCommand {

    PayCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.pay", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length < 2) {
            send(sender, "command.usage.pay");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "pay.self");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "error.invalid_number", Placeholder.unparsed("input", args[1]));
            return;
        }
        if (amount <= 0) {
            send(sender, "error.negative_amount");
            return;
        }
        EconomyService economy = services().economy;
        economy.transferAtomic(player.getUniqueId(), target.getUniqueId(), amount)
                .thenAccept(
                        result -> {
                            String formatted = economy.currency("default").format(amount);
                            if (result.status() == EconomyResult.Status.INSUFFICIENT_FUNDS) {
                                send(
                                        sender,
                                        "pay.insufficient",
                                        Placeholder.unparsed("amount", formatted));
                                return;
                            }
                            if (!result.isSuccess()) {
                                send(sender, "error.internal");
                                return;
                            }
                            send(
                                    sender,
                                    "pay.sent",
                                    Placeholder.unparsed("amount", formatted),
                                    Placeholder.unparsed("player", target.getName()));
                            send(
                                    target,
                                    "pay.received",
                                    Placeholder.unparsed("amount", formatted),
                                    Placeholder.unparsed("player", player.getName()));
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}

final class EcoCommand extends YnmCommand {

    EcoCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.eco.admin", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            send(sender, "command.usage.eco");
            return;
        }
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            send(sender, "error.invalid_number", Placeholder.unparsed("input", args[2]));
            return;
        }
        EconomyService economy = services().economy;
        String formatted = economy.currency("default").format(amount);
        if ("@everyone".equalsIgnoreCase(args[1])) {
            List<Player> targets = List.copyOf(Bukkit.getOnlinePlayers());
            for (Player target : targets) {
                applyEco(economy, action, target.getUniqueId(), amount);
            }
            send(
                    sender,
                    switch (action) {
                        case "give" -> "eco.give.everyone";
                        case "take" -> "eco.take.everyone";
                        case "set" -> "eco.set.everyone";
                        default -> "eco.reset.everyone";
                    },
                    Placeholder.unparsed("amount", formatted),
                    Placeholder.unparsed("count", String.valueOf(targets.size())));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetId = target.getUniqueId();
        String targetName = String.valueOf(target.getName());
        switch (action) {
            case "give" ->
                    applyEco(economy, "give", targetId, amount)
                            .thenAccept(
                                    r -> {
                                        send(
                                                sender,
                                                "eco.give",
                                                Placeholder.unparsed("amount", formatted),
                                                Placeholder.unparsed("player", targetName));
                                        notifyIfOnline(targetId, "eco.give.notify", formatted);
                                    });
            case "take" ->
                    applyEco(economy, "take", targetId, amount)
                            .thenAccept(
                                    r -> {
                                        if (!r.isSuccess()) {
                                            send(
                                                    sender,
                                                    "eco.take.failed",
                                                    Placeholder.unparsed("amount", formatted),
                                                    Placeholder.unparsed("player", targetName));
                                            return;
                                        }
                                        send(
                                                sender,
                                                "eco.take",
                                                Placeholder.unparsed("amount", formatted),
                                                Placeholder.unparsed("player", targetName));
                                        notifyIfOnline(targetId, "eco.take.notify", formatted);
                                    });
            case "set" ->
                    economy.setBalance(targetId, amount)
                            .thenAccept(
                                    r -> {
                                        send(
                                                sender,
                                                "eco.set",
                                                Placeholder.unparsed("amount", formatted),
                                                Placeholder.unparsed("player", targetName));
                                        notifyIfOnline(targetId, "eco.set.notify", formatted);
                                    });
            default ->
                    economy.setBalance(targetId, amount)
                            .thenAccept(
                                    r -> {
                                        send(
                                                sender,
                                                "eco.reset",
                                                Placeholder.unparsed("balance", formatted),
                                                Placeholder.unparsed("player", targetName));
                                        notifyIfOnline(targetId, "eco.reset.notify", formatted);
                                    });
        }
    }

    private java.util.concurrent.CompletableFuture<EconomyResult> applyEco(
            EconomyService economy, String action, UUID target, double amount) {
        return "take".equals(action)
                ? economy.withdraw(target, amount)
                : economy.deposit(target, amount);
    }

    private void notifyIfOnline(UUID target, String key, String formatted) {
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            send(online, key, Placeholder.unparsed("amount", formatted));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("give", "take", "set", "reset");
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}

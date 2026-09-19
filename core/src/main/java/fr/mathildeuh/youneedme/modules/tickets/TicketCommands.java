package fr.mathildeuh.youneedme.modules.tickets;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.tickets.Ticket;
import fr.mathildeuh.youneedme.api.tickets.TicketMessage;
import fr.mathildeuh.youneedme.api.tickets.TicketService;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class TicketCommand extends YnmCommand {

    private static final String STAFF_PERMISSION = "youneedme.tickets.staff";

    TicketCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.tickets", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "ticket.usage");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(player);
            case "history" -> history(player);
            case "view" -> view(player, args);
            case "reply" -> reply(player, args);
            case "close" -> close(player, args);
            case "claim" -> claim(player, args);
            default -> create(player, args);
        }
    }

    private void create(Player player, String[] args) {
        if (!plugin.configManager().module("tickets").getBoolean("enabled", true)) {
            send(player, "ticket.disabled");
            return;
        }
        TicketService tickets = services().tickets;
        List<String> categories = tickets.categories();
        String category = null;
        int messageStart = 0;
        if (!categories.isEmpty() && args.length > 1) {
            for (String candidate : categories) {
                if (candidate.equalsIgnoreCase(args[0])) {
                    category = candidate;
                    messageStart = 1;
                    break;
                }
            }
        }
        String message =
                String.join(" ", java.util.Arrays.copyOfRange(args, messageStart, args.length));
        if (message.isBlank()) {
            send(player, "ticket.usage");
            return;
        }
        String finalCategory = category;
        tickets.create(player.getUniqueId(), player.getName(), category, message)
                .thenAccept(
                        ticket ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    send(
                                                            player,
                                                            "ticket.create.success",
                                                            Placeholder.unparsed(
                                                                    "id",
                                                                    String.valueOf(ticket.id())));
                                                    notifyStaff(ticket, finalCategory, message);
                                                    Bukkit.getPluginManager()
                                                            .callEvent(
                                                                    new fr.mathildeuh.youneedme.api
                                                                            .event
                                                                            .TicketCreateEvent(
                                                                            player, ticket));
                                                }))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private void notifyStaff(Ticket ticket, String category, String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(STAFF_PERMISSION)) {
                send(
                        online,
                        "ticket.notify.staff",
                        Placeholder.unparsed("id", String.valueOf(ticket.id())),
                        Placeholder.unparsed("player", ticket.playerLastKnownUsername()),
                        Placeholder.unparsed("category", category == null ? "-" : category),
                        Placeholder.unparsed("message", message));
            }
        }
    }

    private void list(Player player) {
        services()
                .tickets
                .openByPlayer(player.getUniqueId())
                .thenAccept(
                        list ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> sendTicketList(player, list, "ticket.list")))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private void history(Player player) {
        services()
                .tickets
                .history(player.getUniqueId())
                .thenAccept(
                        list ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () ->
                                                        sendTicketList(
                                                                player, list, "ticket.history")))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private void sendTicketList(Player player, List<Ticket> list, String keyPrefix) {
        if (list.isEmpty()) {
            send(player, keyPrefix + ".empty");
            return;
        }
        send(
                player,
                keyPrefix + ".header",
                Placeholder.unparsed("count", String.valueOf(list.size())));
        for (Ticket ticket : list) {
            sendWithRaw(
                    player,
                    keyPrefix + ".entry",
                    java.util.Map.of("id", String.valueOf(ticket.id())),
                    Placeholder.unparsed("id", String.valueOf(ticket.id())),
                    Placeholder.unparsed("status", ticket.status().name()),
                    Placeholder.unparsed(
                            "category", ticket.category() == null ? "-" : ticket.category()));
        }
    }

    private void view(Player player, String[] args) {
        Long id = parseId(player, args);
        if (id == null) {
            return;
        }
        services()
                .tickets
                .find(id)
                .thenAccept(
                        opt ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    if (opt.isEmpty()) {
                                                        send(player, "ticket.view.not_found");
                                                        return;
                                                    }
                                                    Ticket ticket = opt.get();
                                                    if (!canAccess(player, ticket)) {
                                                        send(player, "ticket.view.no_permission");
                                                        return;
                                                    }
                                                    showThread(player, ticket);
                                                }))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private void showThread(Player player, Ticket ticket) {
        send(
                player,
                "ticket.view.header",
                Placeholder.unparsed("id", String.valueOf(ticket.id())),
                Placeholder.unparsed("player", ticket.playerLastKnownUsername()),
                Placeholder.unparsed("status", ticket.status().name()),
                Placeholder.unparsed(
                        "category", ticket.category() == null ? "-" : ticket.category()));
        services()
                .tickets
                .messages(ticket.id())
                .thenAccept(
                        messages ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    for (TicketMessage message : messages) {
                                                        send(
                                                                player,
                                                                "ticket.view.message",
                                                                Placeholder.unparsed(
                                                                        "author",
                                                                        message.authorUsername()),
                                                                Placeholder.unparsed(
                                                                        "message",
                                                                        message.message()),
                                                                Placeholder.unparsed(
                                                                        "time",
                                                                        DateTimeFormatter.ofPattern(
                                                                                        "HH:mm")
                                                                                .withZone(
                                                                                        java.time
                                                                                                .ZoneId
                                                                                                .systemDefault())
                                                                                .format(
                                                                                        Instant
                                                                                                .ofEpochMilli(
                                                                                                        message
                                                                                                                .sentAt()))));
                                                    }
                                                }))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private void reply(Player player, String[] args) {
        Long id = parseId(player, args);
        if (id == null) {
            return;
        }
        if (args.length < 3) {
            send(player, "ticket.usage");
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        boolean staff = player.hasPermission(STAFF_PERMISSION);
        services()
                .tickets
                .find(id)
                .thenCompose(
                        opt -> {
                            if (opt.isEmpty() || !canAccess(player, opt.get())) {
                                return java.util.concurrent.CompletableFuture.completedFuture(
                                        TicketService.ReplyResult.NOT_FOUND);
                            }
                            return services()
                                    .tickets
                                    .reply(
                                            id,
                                            player.getUniqueId(),
                                            player.getName(),
                                            message,
                                            staff);
                        })
                .thenAccept(
                        result ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    switch (result) {
                                                        case SUCCESS -> {
                                                            send(
                                                                    player,
                                                                    "ticket.reply.success",
                                                                    Placeholder.unparsed(
                                                                            "id",
                                                                            String.valueOf(id)));
                                                            notifyReply(id, player, message, staff);
                                                        }
                                                        case CLOSED ->
                                                                send(player, "ticket.reply.closed");
                                                        case NOT_FOUND ->
                                                                send(
                                                                        player,
                                                                        "ticket.view.not_found");
                                                    }
                                                }))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private void notifyReply(long id, Player author, String message, boolean staffReply) {
        services()
                .tickets
                .find(id)
                .thenAccept(
                        opt ->
                                opt.ifPresent(
                                        ticket ->
                                                plugin.scheduler()
                                                        .runGlobal(
                                                                () -> {
                                                                    if (staffReply) {
                                                                        Player owner =
                                                                                Bukkit.getPlayer(
                                                                                        ticket
                                                                                                .player());
                                                                        if (owner != null) {
                                                                            send(
                                                                                    owner,
                                                                                    "ticket.notify.reply",
                                                                                    Placeholder
                                                                                            .unparsed(
                                                                                                    "id",
                                                                                                    String
                                                                                                            .valueOf(
                                                                                                                    id)));
                                                                        }
                                                                    } else {
                                                                        notifyStaff(
                                                                                ticket,
                                                                                ticket.category(),
                                                                                message);
                                                                    }
                                                                })));
    }

    private void close(Player player, String[] args) {
        Long id = parseId(player, args);
        if (id == null) {
            return;
        }
        services()
                .tickets
                .find(id)
                .thenCompose(
                        opt -> {
                            if (opt.isEmpty() || !canAccess(player, opt.get())) {
                                return java.util.concurrent.CompletableFuture.completedFuture(
                                        TicketService.CloseResult.NOT_FOUND);
                            }
                            return services()
                                    .tickets
                                    .close(id, player.getUniqueId(), player.getName());
                        })
                .thenAccept(
                        result ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    switch (result) {
                                                        case SUCCESS ->
                                                                send(
                                                                        player,
                                                                        "ticket.close.success",
                                                                        Placeholder.unparsed(
                                                                                "id",
                                                                                String.valueOf(
                                                                                        id)));
                                                        case ALREADY_CLOSED ->
                                                                send(
                                                                        player,
                                                                        "ticket.close.already_closed");
                                                        case NOT_FOUND ->
                                                                send(
                                                                        player,
                                                                        "ticket.view.not_found");
                                                    }
                                                }))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private void claim(Player player, String[] args) {
        if (!player.hasPermission(STAFF_PERMISSION)) {
            send(player, "error.no_permission");
            return;
        }
        Long id = parseId(player, args);
        if (id == null) {
            return;
        }
        services()
                .tickets
                .claim(id, player.getUniqueId(), player.getName())
                .thenAccept(
                        result ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    switch (result) {
                                                        case SUCCESS ->
                                                                send(
                                                                        player,
                                                                        "ticket.claim.success",
                                                                        Placeholder.unparsed(
                                                                                "id",
                                                                                String.valueOf(
                                                                                        id)));
                                                        case ALREADY_CLAIMED ->
                                                                send(
                                                                        player,
                                                                        "ticket.claim.already_claimed");
                                                        case ALREADY_CLOSED ->
                                                                send(
                                                                        player,
                                                                        "ticket.claim.already_closed");
                                                        case NOT_FOUND ->
                                                                send(
                                                                        player,
                                                                        "ticket.view.not_found");
                                                    }
                                                }))
                .exceptionally(reportAsyncFailure(player, "ticket"));
    }

    private boolean canAccess(Player player, Ticket ticket) {
        return ticket.player().equals(player.getUniqueId())
                || player.hasPermission(STAFF_PERMISSION);
    }

    private Long parseId(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "ticket.usage");
            return null;
        }
        try {
            return Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            send(player, "ticket.usage");
            return null;
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>();
            options.addAll(List.of("list", "history", "view", "reply", "close", "claim"));
            options.addAll(services().tickets.categories());
            return options;
        }
        return List.of();
    }
}

final class TicketsCommand extends YnmCommand {

    private final TicketQueueGui gui;

    TicketsCommand(YouNeedMe plugin, TicketQueueGui gui) {
        super(plugin, "youneedme.tickets.staff", true);
        this.gui = gui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        gui.open(player(sender), 1);
    }
}

package fr.mathildeuh.youneedme.modules.kits;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.kits.Kit;
import fr.mathildeuh.youneedme.api.kits.KitService;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class KitCommand extends YnmCommand {

    private final KitGui gui;

    KitCommand(YouNeedMe plugin, KitGui gui) {
        super(plugin, "youneedme.kit", true);
        this.gui = gui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            gui.open(player, 1);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        KitService kits = services().kits;
        switch (sub) {
            case "cooldown" -> {
                if (args.length < 2) {
                    send(sender, "kit.usage");
                    return;
                }
                kits.cooldownRemaining(player.getUniqueId(), args[1])
                        .thenAccept(
                                remaining -> {
                                    if (remaining <= 0) {
                                        send(
                                                sender,
                                                "kit.cooldown.ready",
                                                Placeholder.unparsed("kit", args[1]));
                                    } else {
                                        send(
                                                sender,
                                                "kit.cooldown.status",
                                                Placeholder.unparsed("kit", args[1]),
                                                Placeholder.unparsed(
                                                        "time",
                                                        TimeParser.format(remaining * 1000)));
                                    }
                                });
                return;
            }
            case "claim" -> {
                if (args.length < 2) {
                    send(sender, "kit.usage");
                    return;
                }
                claim(sender, player, kits, args[1]);
                return;
            }
            case "notifications" -> {
                boolean enabled = services().kits.toggleCooldownNotifications(player.getUniqueId());
                send(sender, enabled ? "kit.notifications.enabled" : "kit.notifications.disabled");
                return;
            }
            case "debug" -> {
                if (args.length < 2) {
                    send(sender, "kit.usage");
                    return;
                }
                debug(sender, kits, args[1]);
                return;
            }
            default -> claim(sender, player, kits, sub);
        }
    }

    private void debug(CommandSender sender, KitService kits, String kitId) {
        kits.kit(kitId)
                .ifPresentOrElse(
                        k -> {
                            send(sender, "kit.debug.header", Placeholder.unparsed("kit", k.id()));
                            send(
                                    sender,
                                    "kit.debug.name",
                                    Placeholder.unparsed("name", k.displayName()));
                            send(
                                    sender,
                                    "kit.debug.permission",
                                    Placeholder.unparsed(
                                            "perm", k.permission() == null ? "-" : k.permission()));
                            send(
                                    sender,
                                    "kit.debug.items",
                                    Placeholder.unparsed(
                                            "count", String.valueOf(k.items().size())));
                            send(
                                    sender,
                                    "kit.debug.cooldown",
                                    Placeholder.unparsed(
                                            "secs", String.valueOf(k.cooldownSeconds())));
                            send(
                                    sender,
                                    "kit.debug.onetime",
                                    Placeholder.unparsed("val", String.valueOf(k.oneTime())));
                            send(
                                    sender,
                                    "kit.debug.maxclaims",
                                    Placeholder.unparsed(
                                            "max",
                                            k.maxClaims() == null
                                                    ? "-"
                                                    : String.valueOf(k.maxClaims())));
                            send(sender, "kit.debug.itemlist");
                            for (var item : k.items()) {
                                if (item != null && !item.getType().isAir()) {
                                    send(
                                            sender,
                                            "kit.debug.item_entry",
                                            Placeholder.unparsed("type", item.getType().name()),
                                            Placeholder.unparsed(
                                                    "amount", String.valueOf(item.getAmount())));
                                }
                            }
                        },
                        () -> send(sender, "kit.not_found", Placeholder.unparsed("kit", kitId)));
    }

    private void claim(CommandSender sender, Player player, KitService kits, String kitId) {
        kits.claim(player.getUniqueId(), kitId)
                .thenAccept(
                        result -> {
                            switch (result) {
                                case SUCCESS ->
                                        send(
                                                sender,
                                                "kit.claim.success",
                                                Placeholder.unparsed("kit", kitId));
                                case KIT_NOT_FOUND ->
                                        send(
                                                sender,
                                                "kit.not_found",
                                                Placeholder.unparsed("kit", kitId));
                                case NO_PERMISSION ->
                                        send(
                                                sender,
                                                "kit.no_permission",
                                                Placeholder.unparsed("kit", kitId));
                                case ALREADY_CLAIMED_ONE_TIME ->
                                        send(
                                                sender,
                                                "kit.one_time_used",
                                                Placeholder.unparsed("kit", kitId));
                                case MAX_CLAIMS_REACHED ->
                                        kits.kit(kitId)
                                                .ifPresent(
                                                        k ->
                                                                send(
                                                                        sender,
                                                                        "kit.max_claims_reached",
                                                                        Placeholder.unparsed(
                                                                                "max",
                                                                                String.valueOf(
                                                                                        k
                                                                                                .maxClaims())),
                                                                        Placeholder.unparsed(
                                                                                "kit", kitId)));
                                case ON_COOLDOWN ->
                                        kits.cooldownRemaining(player.getUniqueId(), kitId)
                                                .thenAccept(
                                                        remaining ->
                                                                send(
                                                                        sender,
                                                                        "kit.cooldown_active",
                                                                        Placeholder.unparsed(
                                                                                "kit", kitId),
                                                                        Placeholder.unparsed(
                                                                                "time",
                                                                                TimeParser.format(
                                                                                        remaining
                                                                                                * 1000))));
                                case INVENTORY_FULL -> send(sender, "shop.inventory-full");
                            }
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options =
                    new java.util.ArrayList<>(
                            List.of("claim", "cooldown", "notifications", "debug"));
            services().kits.kits().forEach(k -> options.add(k.id()));
            return options;
        }
        if (args.length == 2) {
            return services().kits.kits().stream().map(Kit::id).toList();
        }
        return List.of();
    }
}

final class KitsCommand extends YnmCommand {

    private final KitEditorGui editorGui;

    KitsCommand(YouNeedMe plugin, KitEditorGui editorGui) {
        super(plugin, "youneedme.kits.list", false);
        this.editorGui = editorGui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        if ("reload".equals(sub)) {
            services().kits.reloadFrom(KitLoader.load(plugin.configManager().kits()));
            send(sender, "kits.reload.success");
            return;
        }
        if ("edit".equals(sub)) {
            if (!sender.hasPermission("youneedme.admin")) {
                send(sender, "error.no_permission");
                return;
            }
            editorGui.openList(player(sender));
            return;
        }
        var kits = services().kits.kits();
        if (kits.isEmpty()) {
            send(sender, "kits.list.no_kits");
            return;
        }
        send(
                sender,
                "kits.list.header",
                Placeholder.unparsed("count", String.valueOf(kits.size())));
        for (Kit kit : kits) {
            send(
                    sender,
                    "kits.list.entry",
                    Placeholder.unparsed("status", "•"),
                    Placeholder.unparsed("kit", kit.id()),
                    Placeholder.unparsed("name", kit.displayName()));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("list", "reload", "edit") : List.of();
    }
}

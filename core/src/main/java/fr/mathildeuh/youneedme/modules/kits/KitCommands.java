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

    KitCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.kit", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "kit.usage");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        KitService kits = services().kits;
        if ("cooldown".equals(sub)) {
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
                                                    "time", TimeParser.format(remaining * 1000)));
                                }
                            });
            return;
        }
        String kitId = "claim".equals(sub) && args.length > 1 ? args[1] : sub;
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

    KitsCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.kits.list", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        if ("reload".equals(sub)) {
            send(sender, "kits.reload.success");
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
        return args.length == 1 ? List.of("list", "reload") : List.of();
    }
}

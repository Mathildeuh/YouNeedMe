package fr.mathildeuh.youneedme.modules.shop;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ShopCommand extends YnmCommand {

    private final ShopGui gui;
    private final ShopEditorGui editorGui;

    ShopCommand(YouNeedMe plugin, ShopGui gui, ShopEditorGui editorGui) {
        super(plugin, "youneedme.shop", true);
        this.gui = gui;
        this.editorGui = editorGui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            services().shop.reload().thenRun(() -> send(sender, "shop.reload-success"));
            return;
        }
        if (args.length > 0 && "edit".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("youneedme.admin")) {
                send(sender, "error.no_permission");
                return;
            }
            editorGui.openList(player);
            return;
        }
        if (args.length > 0) {
            var category = services().shop.category(args[0]);
            if (category.isPresent()) {
                gui.openCategory(player, category.get());
                return;
            }
            send(sender, "shop.invalid-category");
            return;
        }
        gui.openCategories(player);
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? services().shop.categories().stream().map(c -> c.id()).toList()
                : List.of();
    }
}

final class SellCommand extends YnmCommand {

    private final ShopGui gui;

    SellCommand(YouNeedMe plugin, ShopGui gui) {
        super(plugin, "youneedme.sell", true);
        this.gui = gui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        gui.openCategories(player(sender));
    }
}

final class QuickSellCommand extends YnmCommand {

    QuickSellCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.quicksell.hand", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        boolean whole = args.length > 0 && "inventory".equalsIgnoreCase(args[0]);
        services()
                .shop
                .quickSell(player.getUniqueId(), whole)
                .thenAccept(
                        result -> {
                            if (result.itemsSold() == 0) {
                                send(sender, "shop.not-enough-items");
                                return;
                            }
                            send(
                                    sender,
                                    whole
                                            ? "quicksell.success.inventory"
                                            : "quicksell.success.hand",
                                    Placeholder.unparsed(
                                            "amount", String.valueOf(result.itemsSold())),
                                    Placeholder.unparsed(
                                            "worth", String.valueOf(result.totalWorth())),
                                    Placeholder.unparsed(
                                            "currency",
                                            services().economy.currency("default").symbol()));
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("hand", "inventory") : List.of();
    }
}

final class WorthCommand extends YnmCommand {

    WorthCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.worth.hand", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length > 0 && "inventory".equalsIgnoreCase(args[0])) {
            double total = 0;
            for (var stack : player.getInventory().getStorageContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    total += services().shop.worth(stack) * stack.getAmount();
                }
            }
            send(sender, "worth.inventory.header");
            send(
                    sender,
                    "worth.inventory.total",
                    Placeholder.unparsed("worth", String.valueOf(total)),
                    Placeholder.unparsed(
                            "currency", services().economy.currency("default").symbol()));
            return;
        }
        var hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "worth.hand.empty");
            return;
        }
        double worth = services().shop.worth(hand) * hand.getAmount();
        send(
                sender,
                "worth.hand.header",
                Placeholder.unparsed("item", hand.getType().name()),
                Placeholder.unparsed("amount", String.valueOf(hand.getAmount())),
                Placeholder.unparsed("worth", String.valueOf(worth)),
                Placeholder.unparsed("currency", services().economy.currency("default").symbol()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("inventory") : List.of();
    }
}

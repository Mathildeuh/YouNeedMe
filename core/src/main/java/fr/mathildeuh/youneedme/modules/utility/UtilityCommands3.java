package fr.mathildeuh.youneedme.modules.utility;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class CraftingTableCommand extends YnmCommand {

    CraftingTableCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.craftingtable", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        player(sender).openWorkbench(null, true);
        send(sender, "craftingtable.opened");
    }
}

final class AnvilCommand extends YnmCommand {

    AnvilCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.anvil", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        // A null location makes this a fully virtual anvil with no backing block, which is where
        // shift-click-to-quick-move gets unreliable on some versions - anchoring it to the
        // player's own location keeps it "virtual" (force=true, nothing is placed) while giving
        // the server a real position to resolve container interactions against.
        player.openAnvil(player.getLocation(), true);
        send(sender, "anvil.opened");
    }
}

final class StonecutterCommand extends YnmCommand {

    StonecutterCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.stonecutter", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        player.openInventory(
                Bukkit.createInventory(null, org.bukkit.event.inventory.InventoryType.STONECUTTER));
        send(sender, "stonecutter.opened");
    }
}

final class EnderChestCommand extends YnmCommand {

    EnderChestCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.enderchest", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : player;
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        player.openInventory(target.getEnderChest());
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "enderchest.opened");
        } else {
            send(
                    sender,
                    "enderchest.opened.other",
                    Placeholder.unparsed("target", target.getName()));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class TrashCommand extends YnmCommand implements Listener {

    TrashCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.trash", true);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        27,
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<#444444>Trash"));
        player(sender).openInventory(inventory);
        send(sender, "trash.opened");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView()
                .title()
                .equals(
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<#444444>Trash"))) {
            event.getInventory().clear();
        }
    }
}

final class ItemIdCommand extends YnmCommand {

    ItemIdCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.itemid", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "itemid.empty_hand");
            return;
        }
        send(
                sender,
                "itemid.header",
                Placeholder.unparsed("material", hand.getType().name()),
                Placeholder.unparsed("id", hand.getType().getKey().toString()),
                Placeholder.unparsed("amount", String.valueOf(hand.getAmount())),
                Placeholder.unparsed("max_stack", String.valueOf(hand.getMaxStackSize())));
        ItemMeta meta = hand.getItemMeta();
        Component displayName = meta == null ? null : meta.displayName();
        if (displayName != null) {
            send(sender, "itemid.display_name", Placeholder.component("name", displayName));
        }
        if (!hand.getEnchantments().isEmpty()) {
            String enchants =
                    hand.getEnchantments().entrySet().stream()
                            .map(e -> e.getKey().getKey().getKey() + " " + e.getValue())
                            .collect(java.util.stream.Collectors.joining(", "));
            send(sender, "itemid.enchantments", Placeholder.unparsed("enchantments", enchants));
        }
        send(sender, "itemid.footer");
    }
}

final class SpawnEntityCommand extends YnmCommand {

    SpawnEntityCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.spawnentity", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.spawnentity");
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[0].toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "spawnentity.invalid", Placeholder.unparsed("entity", args[0]));
            return;
        }
        if (!type.isSpawnable()) {
            send(sender, "spawnentity.not_spawnable", Placeholder.unparsed("entity", args[0]));
            return;
        }
        if (!sender.hasPermission(
                        "youneedme.spawnentity." + type.name().toLowerCase(java.util.Locale.ROOT))
                && !sender.hasPermission("youneedme.spawnentity.*")) {
            send(
                    sender,
                    "spawnentity.no_permission_entity",
                    Placeholder.unparsed("entity", args[0]));
            return;
        }
        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                send(sender, "spawnentity.invalid_amount");
                return;
            }
        }
        if (amount < 1) {
            send(sender, "spawnentity.invalid_amount");
            return;
        }
        int max = plugin.configManager().main().getInt("utility.spawnentity-max-amount", 50);
        if (amount > max) {
            amount = max;
            send(
                    sender,
                    "spawnentity.amount_capped",
                    Placeholder.unparsed("max", String.valueOf(max)));
        }
        Player player = player(sender);
        Location loc = player.getLocation();
        EntityType finalType = type;
        int finalAmount = amount;
        plugin.scheduler()
                .runAtLocation(
                        loc,
                        () -> {
                            for (int i = 0; i < finalAmount; i++) {
                                loc.getWorld().spawnEntity(loc, finalType);
                            }
                        });
        send(
                sender,
                "spawnentity.success",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("entity", type.name()));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.stream(EntityType.values())
                    .filter(EntityType::isSpawnable)
                    .map(EntityType::name)
                    .toList();
        }
        return List.of();
    }
}

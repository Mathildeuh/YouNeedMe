package fr.mathildeuh.youneedme.modules.utility;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class GameModeCommand extends YnmCommand {

    private final GameMode fixedMode;

    GameModeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.gamemode", false);
        this.fixedMode = null;
    }

    GameModeCommand(YouNeedMe plugin, GameMode fixedMode, String permission) {
        super(plugin, permission, false);
        this.fixedMode = fixedMode;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        GameMode mode = fixedMode;
        int nameIndex = 0;
        if (mode == null) {
            if (args.length == 0) {
                send(sender, "gamemode.invalid", Placeholder.unparsed("input", ""));
                return;
            }
            mode = parseMode(args[0]);
            if (mode == null) {
                send(sender, "gamemode.invalid", Placeholder.unparsed("input", args[0]));
                return;
            }
            nameIndex = 1;
        }
        Player target =
                args.length > nameIndex
                        ? Bukkit.getPlayerExact(args[nameIndex])
                        : (sender instanceof Player p ? p : null);
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        target.setGameMode(mode);
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        if (self) {
            send(sender, "gamemode.changed", Placeholder.unparsed("mode", mode.name()));
        } else {
            send(
                    sender,
                    "gamemode.changed.other",
                    Placeholder.unparsed("target", target.getName()),
                    Placeholder.unparsed("mode", mode.name()));
            send(
                    target,
                    "gamemode.changed.by",
                    Placeholder.unparsed("mode", mode.name()),
                    Placeholder.unparsed("player", senderName(sender)));
        }
    }

    private static GameMode parseMode(String input) {
        return switch (input.toLowerCase(java.util.Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (fixedMode == null && args.length == 1) {
            return List.of("survival", "creative", "adventure", "spectator");
        }
        int nameIndex = fixedMode == null ? 1 : 0;
        if (args.length == nameIndex + 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}

final class HatCommand extends YnmCommand {

    HatCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.hat", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "hat.no_item");
            return;
        }
        if (hand.getType().isBlock()) {
            send(sender, "hat.no_blocks");
            return;
        }
        ItemStack currentHelmet = player.getInventory().getHelmet();
        if (currentHelmet != null) {
            ItemMeta meta = currentHelmet.getItemMeta();
            if (meta != null && meta.hasEnchant(Enchantment.BINDING_CURSE)) {
                send(sender, "hat.binding_curse");
                return;
            }
        }
        player.getInventory().setHelmet(hand.clone());
        player.getInventory().setItemInMainHand(currentHelmet);
        send(sender, "hat.success", Placeholder.unparsed("item", hand.getType().name()));
    }
}

final class RenameCommand extends YnmCommand {

    RenameCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.rename", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.rename");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "rename.no_item");
            return;
        }
        String name = String.join(" ", args);
        int maxLength = plugin.configManager().main().getInt("utility.rename-max-length", 32);
        if (name.length() > maxLength) {
            send(
                    sender,
                    "rename.too_long",
                    Placeholder.unparsed("max", String.valueOf(maxLength)),
                    Placeholder.unparsed("current", String.valueOf(name.length())));
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            send(sender, "rename.cannot_rename");
            return;
        }
        meta.displayName(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        hand.setItemMeta(meta);
        send(sender, "rename.success", Placeholder.unparsed("name", name));
    }
}

final class RepairCommand extends YnmCommand {

    RepairCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.repair", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : player;
        if (target == null) {
            send(sender, "error.player_not_found");
            return;
        }
        ItemStack hand = target.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "repair.error.no_item");
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            send(sender, "repair.error.not_repairable");
            return;
        }
        if (damageable.getDamage() == 0) {
            send(sender, "repair.error.already_repaired");
            return;
        }
        damageable.setDamage(0);
        hand.setItemMeta((ItemMeta) damageable);
        boolean self = target.getUniqueId().equals(player.getUniqueId());
        if (self) {
            send(sender, "repair.success", Placeholder.unparsed("item", hand.getType().name()));
        } else {
            send(
                    sender,
                    "repair.success.other",
                    Placeholder.unparsed("target", target.getName()),
                    Placeholder.unparsed("item", hand.getType().name()));
            send(
                    target,
                    "repair.success.by",
                    Placeholder.unparsed("item", hand.getType().name()),
                    Placeholder.unparsed("repairer", senderName(sender)));
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

final class EnchantCommand extends YnmCommand {

    EnchantCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.enchant", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "command.usage.enchant");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "enchant.no_item");
            return;
        }
        Enchantment enchantment = resolveEnchantment(args[0]);
        if (enchantment == null) {
            send(sender, "enchant.invalid", Placeholder.unparsed("enchantment", args[0]));
            return;
        }
        int level = enchantment.getMaxLevel();
        if (args.length > 1) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                send(sender, "enchant.invalid_level");
                return;
            }
        }
        if (level < 1) {
            send(sender, "enchant.invalid_level");
            return;
        }
        hand.addUnsafeEnchantment(enchantment, level);
        send(
                sender,
                "enchant.success",
                Placeholder.unparsed("enchantment", enchantment.getKey().getKey()),
                Placeholder.unparsed("level", String.valueOf(level)));
    }

    static Enchantment resolveEnchantment(String input) {
        org.bukkit.NamespacedKey key =
                org.bukkit.NamespacedKey.minecraft(
                        input.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }
}

final class UnenchantCommand extends YnmCommand {

    UnenchantCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.unenchant", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "enchant.no_item");
            return;
        }
        if (args.length == 0) {
            hand.getEnchantments().keySet().forEach(hand::removeEnchantment);
            send(sender, "unenchant.success.all");
            return;
        }
        Enchantment enchantment = EnchantCommand.resolveEnchantment(args[0]);
        if (enchantment == null || !hand.containsEnchantment(enchantment)) {
            send(sender, "unenchant.not_found", Placeholder.unparsed("enchantment", args[0]));
            return;
        }
        hand.removeEnchantment(enchantment);
        send(
                sender,
                "unenchant.success.single",
                Placeholder.unparsed("enchantment", enchantment.getKey().getKey()));
    }
}

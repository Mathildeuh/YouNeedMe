package fr.mathildeuh.youneedme.modules.admin;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.moderation.Punishment;
import fr.mathildeuh.youneedme.api.moderation.PunishmentType;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * A one-screen staff overview ({@code /ynm panel}): who's online, top balances, active bans/mutes,
 * whether cross-server vanish sync is connected, server uptime, and one-click launchers into the
 * kit/shop editors and the auction house - instead of remembering half a dozen separate commands.
 * Entirely read-only aside from the launcher buttons, which just run the same commands a staff
 * member would type themselves (same permission checks apply).
 */
public final class AdminDashboardGui implements Listener {

    private final YouNeedMe plugin;
    private final NamespacedKey commandKey;

    public AdminDashboardGui(YouNeedMe plugin) {
        this.plugin = plugin;
        this.commandKey = new NamespacedKey(plugin, "panel-command");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        CompletableFuture<List<EconomyService.BalanceEntry>> topBalances =
                plugin.services().economy.top("default", 1, 3);
        CompletableFuture<List<Punishment>> activeBans =
                plugin.services().moderation.activePunishments(PunishmentType.BAN, 1, 5);
        CompletableFuture<List<Punishment>> activeMutes =
                plugin.services().moderation.activePunishments(PunishmentType.MUTE, 1, 5);
        CompletableFuture.allOf(topBalances, activeBans, activeMutes)
                .thenAccept(
                        v ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () ->
                                                        render(
                                                                player,
                                                                topBalances.join(),
                                                                activeBans.join(),
                                                                activeMutes.join())));
    }

    private void render(
            Player player,
            List<EconomyService.BalanceEntry> topBalances,
            List<Punishment> activeBans,
            List<Punishment> activeMutes) {
        Inventory inventory =
                Bukkit.createInventory(
                        new Holder(),
                        27,
                        noItalic(Component.text("YouNeedMe - Staff Panel", NamedTextColor.GOLD)));
        inventory.setItem(10, onlineIcon());
        inventory.setItem(11, economyIcon(topBalances));
        inventory.setItem(12, moderationIcon(activeBans, activeMutes));
        inventory.setItem(13, networkIcon());
        inventory.setItem(14, uptimeIcon());
        inventory.setItem(19, launchIcon(Material.CHEST, "Kit Editor", "kits edit"));
        inventory.setItem(20, launchIcon(Material.EMERALD, "Shop Editor", "shop edit"));
        inventory.setItem(21, launchIcon(Material.GOLD_INGOT, "Auction House", "ah"));
        inventory.setItem(22, launchIcon(Material.BOOK, "Ban List", "banlist"));
        inventory.setItem(23, launchIcon(Material.PAPER, "Balance Top", "baltop"));
        player.openInventory(inventory);
    }

    private ItemStack onlineIcon() {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(
                noItalic(
                        Component.text(
                                Bukkit.getOnlinePlayers().size()
                                        + "/"
                                        + Bukkit.getMaxPlayers()
                                        + " online",
                                NamedTextColor.AQUA)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack economyIcon(List<EconomyService.BalanceEntry> top) {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(Component.text("Top Balances", NamedTextColor.YELLOW)));
        var currency = plugin.services().economy.currency("default");
        List<Component> lore = new ArrayList<>();
        if (top.isEmpty()) {
            lore.add(noItalic(Component.text("No entries yet.", NamedTextColor.GRAY)));
        } else {
            int rank = 1;
            for (var entry : top) {
                lore.add(
                        noItalic(
                                Component.text(
                                        "#"
                                                + rank++
                                                + " "
                                                + entry.lastKnownUsername()
                                                + " - "
                                                + currency.format(entry.balance()),
                                        NamedTextColor.GRAY)));
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack moderationIcon(List<Punishment> bans, List<Punishment> mutes) {
        ItemStack stack = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(Component.text("Active Punishments", NamedTextColor.RED)));
        List<Component> lore = new ArrayList<>();
        lore.add(
                noItalic(
                        Component.text(
                                bans.size() + (bans.size() == 5 ? "+" : "") + " active ban(s)",
                                NamedTextColor.GRAY)));
        lore.add(
                noItalic(
                        Component.text(
                                mutes.size() + (mutes.size() == 5 ? "+" : "") + " active mute(s)",
                                NamedTextColor.GRAY)));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack networkIcon() {
        boolean connected = plugin.services().networkVanish.isEnabled();
        ItemStack stack = new ItemStack(connected ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(
                noItalic(
                        Component.text(
                                "Cross-server vanish: " + (connected ? "connected" : "off"),
                                connected ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack uptimeIcon() {
        long uptimeMillis =
                System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
        ItemStack stack = new ItemStack(Material.CLOCK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(
                noItalic(
                        Component.text(
                                "Uptime: " + TimeParser.format(uptimeMillis),
                                NamedTextColor.WHITE)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack launchIcon(Material material, String name, String command) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD)));
        meta.lore(List.of(noItalic(Component.text("Click to open", NamedTextColor.GRAY))));
        meta.getPersistentDataContainer().set(commandKey, PersistentDataType.STRING, command);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) {
            return;
        }
        String command =
                clicked.getItemMeta()
                        .getPersistentDataContainer()
                        .get(commandKey, PersistentDataType.STRING);
        if (command == null) {
            return;
        }
        player.closeInventory();
        plugin.scheduler().runGlobal(() -> player.performCommand(command));
    }

    private record Holder() implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

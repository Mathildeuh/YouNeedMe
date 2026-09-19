package fr.mathildeuh.youneedme.modules.trade;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.economy.Currency;
import fr.mathildeuh.youneedme.api.event.TradeCompletedEvent;
import fr.mathildeuh.youneedme.gui.DialogInputPrompt;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * The trade window itself: one shared {@link Inventory} both players open, so their views are
 * always in sync by construction - no manual mirroring. Item slots are left uncancelled for their
 * owning player (Bukkit's native drag/shift-click/hotbar-swap handles placement correctly on its
 * own; the slot range being physically inside the trade GUI at all IS the escrow) and cancelled for
 * the other player. Money/XP are withdrawn from the offering player the instant they're set (not
 * only at final exchange) for the same reason: what's visibly "on the table" must always be real,
 * already-committed funds, never a number either player could still spend elsewhere.
 */
public final class TradeGui implements Listener {

    private static final int[] SLOTS_A = {
        0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30
    };
    private static final int[] SLOTS_B = {
        5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35
    };
    private static final int[] DIVIDER_SLOTS = {4, 13, 22, 31, 39, 41};
    private static final int SLOT_MONEY_A = 36;
    private static final int SLOT_XP_A = 37;
    private static final int SLOT_READY_A = 38;
    private static final int SLOT_INFO = 40;
    private static final int SLOT_READY_B = 42;
    private static final int SLOT_XP_B = 43;
    private static final int SLOT_MONEY_B = 44;
    private static final int SLOT_CANCEL = 49;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YouNeedMe plugin;
    private final TradeManager manager;

    public TradeGui(YouNeedMe plugin, TradeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player a, Player b) {
        Inventory inventory =
                Bukkit.createInventory(new TradeHolder(), 54, Component.text("Trade"));
        TradeSession session = new TradeSession(a.getUniqueId(), b.getUniqueId(), inventory);
        manager.startSession(session);
        render(session);
        a.openInventory(inventory);
        b.openInventory(inventory);
    }

    private void render(TradeSession session) {
        Inventory inventory = session.inventory;
        for (int slot : DIVIDER_SLOTS) {
            inventory.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int slot = 45; slot < 54; slot++) {
            if (slot != SLOT_CANCEL) {
                inventory.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
        Currency currency = plugin.services().economy.currency("default");
        inventory.setItem(SLOT_MONEY_A, moneyIcon(session.moneyA, currency));
        inventory.setItem(SLOT_MONEY_B, moneyIcon(session.moneyB, currency));
        inventory.setItem(SLOT_XP_A, xpIcon(session.xpLevelsA));
        inventory.setItem(SLOT_XP_B, xpIcon(session.xpLevelsB));
        inventory.setItem(SLOT_READY_A, readyIcon(session.readyA));
        inventory.setItem(SLOT_READY_B, readyIcon(session.readyB));
        inventory.setItem(
                SLOT_INFO,
                pane(
                        Material.PAPER,
                        "<yellow>Drag items into your side. Set money/XP below. Both ready = trade"
                                + " happens."));
        inventory.setItem(SLOT_CANCEL, pane(Material.BARRIER, "<red><bold>Cancel Trade"));
    }

    private ItemStack moneyIcon(double amount, Currency currency) {
        return pane(Material.GOLD_INGOT, "<gold>Offering: <white>" + currency.format(amount));
    }

    private ItemStack xpIcon(int levels) {
        return pane(Material.EXPERIENCE_BOTTLE, "<green>Offering: <white>" + levels + " levels");
    }

    private ItemStack readyIcon(boolean ready) {
        return ready
                ? pane(Material.LIME_DYE, "<green><bold>READY <gray>(click to unready)")
                : pane(Material.RED_DYE, "<red>Not ready <gray>(click when your offer is final)");
    }

    private ItemStack pane(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(
                MM.deserialize(name)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    // --- Clicks ----------------------------------------------------------------------------

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder)) {
            return;
        }
        TradeSession session =
                manager.activeSession(event.getWhoClicked().getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        int[] ownSlots = session.isPlayerA(event.getWhoClicked().getUniqueId()) ? SLOTS_A : SLOTS_B;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && !contains(ownSlots, slot)) {
                event.setCancelled(true);
                return;
            }
        }
        plugin.scheduler().runGlobal(() -> onOfferChanged(session));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TradeSession session = manager.activeSession(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (slot >= topSize) {
            // The player's own inventory (bottom half) - never restricted, but a shift-click from
            // here into their trade slots still counts as an offer change, checked next tick once
            // Bukkit has actually moved the item.
            plugin.scheduler().runGlobal(() -> onOfferChanged(session));
            return;
        }
        boolean isA = session.isPlayerA(player.getUniqueId());
        int[] ownSlots = isA ? SLOTS_A : SLOTS_B;
        if (contains(ownSlots, slot)) {
            plugin.scheduler().runGlobal(() -> onOfferChanged(session));
            return;
        }
        event.setCancelled(true);
        if (slot == SLOT_CANCEL) {
            cancel(session, "trade.cancelled.by_player");
            return;
        }
        if (isA && slot == SLOT_MONEY_A) {
            promptMoney(player, session, true);
        } else if (isA && slot == SLOT_XP_A) {
            promptXp(player, session, true);
        } else if (isA && slot == SLOT_READY_A) {
            toggleReady(session, true);
        } else if (!isA && slot == SLOT_MONEY_B) {
            promptMoney(player, session, false);
        } else if (!isA && slot == SLOT_XP_B) {
            promptXp(player, session, false);
        } else if (!isA && slot == SLOT_READY_B) {
            toggleReady(session, false);
        }
    }

    private void onOfferChanged(TradeSession session) {
        if (!session.readyA && !session.readyB) {
            return;
        }
        session.resetReady();
        render(session);
        notifyBoth(session, "trade.offer_changed");
    }

    private void toggleReady(TradeSession session, boolean isA) {
        if (isA) {
            session.readyA = !session.readyA;
        } else {
            session.readyB = !session.readyB;
        }
        render(session);
        if (session.readyA && session.readyB) {
            execute(session);
        }
    }

    private void promptMoney(Player player, TradeSession session, boolean isA) {
        double current = isA ? session.moneyA : session.moneyB;
        DialogInputPrompt.open(
                player,
                "Offer money",
                "Amount",
                current == 0 ? "" : String.valueOf((long) current),
                text -> {
                    Double amount = parseNonNegative(text);
                    if (amount == null) {
                        player.sendMessage(
                                plugin.lang().render(player, "trade.error.invalid_amount"));
                        return;
                    }
                    reescrowMoney(player, session, isA, amount);
                });
    }

    private void reescrowMoney(Player player, TradeSession session, boolean isA, double newAmount) {
        double previous = isA ? session.moneyA : session.moneyB;
        var economy = plugin.services().economy;
        economy.deposit(player.getUniqueId(), previous)
                .thenCompose(
                        r ->
                                newAmount > 0
                                        ? economy.withdraw(player.getUniqueId(), newAmount)
                                        : java.util.concurrent.CompletableFuture.completedFuture(
                                                fr.mathildeuh.youneedme.api.economy.EconomyResult
                                                        .success(0)))
                .thenAccept(
                        result ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    if (result.isSuccess()) {
                                                        setMoney(session, isA, newAmount);
                                                    } else {
                                                        setMoney(session, isA, 0);
                                                        player.sendMessage(
                                                                plugin.lang()
                                                                        .render(
                                                                                player,
                                                                                "trade.error.insufficient_funds"));
                                                    }
                                                    session.resetReady();
                                                    render(session);
                                                }));
    }

    private void setMoney(TradeSession session, boolean isA, double amount) {
        if (isA) {
            session.moneyA = amount;
        } else {
            session.moneyB = amount;
        }
    }

    private void promptXp(Player player, TradeSession session, boolean isA) {
        int current = isA ? session.xpLevelsA : session.xpLevelsB;
        DialogInputPrompt.open(
                player,
                "Offer XP levels",
                "Levels",
                current == 0 ? "" : String.valueOf(current),
                text -> {
                    Integer levels = parseNonNegativeInt(text);
                    if (levels == null) {
                        player.sendMessage(
                                plugin.lang().render(player, "trade.error.invalid_amount"));
                        return;
                    }
                    reescrowXp(player, session, isA, levels);
                });
    }

    private void reescrowXp(Player player, TradeSession session, boolean isA, int newLevels) {
        int previous = isA ? session.xpLevelsA : session.xpLevelsB;
        player.setLevel(player.getLevel() + previous);
        if (newLevels > player.getLevel()) {
            setXp(session, isA, 0);
            player.sendMessage(plugin.lang().render(player, "trade.error.insufficient_xp"));
        } else {
            player.setLevel(player.getLevel() - newLevels);
            setXp(session, isA, newLevels);
        }
        session.resetReady();
        render(session);
    }

    private void setXp(TradeSession session, boolean isA, int levels) {
        if (isA) {
            session.xpLevelsA = levels;
        } else {
            session.xpLevelsB = levels;
        }
    }

    private static Double parseNonNegative(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseNonNegativeInt(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Execution ---------------------------------------------------------------------------

    private void execute(TradeSession session) {
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);
        if (a == null || b == null) {
            cancel(session, "trade.cancelled.disconnected");
            return;
        }
        List<ItemStack> itemsA = takeItems(session.inventory, SLOTS_A);
        List<ItemStack> itemsB = takeItems(session.inventory, SLOTS_B);
        if (!hasSpaceFor(b.getInventory(), itemsA) || !hasSpaceFor(a.getInventory(), itemsB)) {
            putBack(session.inventory, SLOTS_A, itemsA);
            putBack(session.inventory, SLOTS_B, itemsB);
            session.resetReady();
            render(session);
            notifyBoth(session, "trade.error.no_space");
            return;
        }
        var economy = plugin.services().economy;
        economy.deposit(a.getUniqueId(), session.moneyB);
        economy.deposit(b.getUniqueId(), session.moneyA);
        a.setLevel(a.getLevel() + session.xpLevelsB);
        b.setLevel(b.getLevel() + session.xpLevelsA);
        itemsA.forEach(item -> giveOrDrop(b, item));
        itemsB.forEach(item -> giveOrDrop(a, item));

        manager.endSession(session);
        a.sendMessage(plugin.lang().render(a, "trade.success"));
        b.sendMessage(plugin.lang().render(b, "trade.success"));
        a.closeInventory();
        b.closeInventory();
        Bukkit.getPluginManager()
                .callEvent(new TradeCompletedEvent(session.playerA, session.playerB));
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory()
                .addItem(item)
                .values()
                .forEach(
                        leftover ->
                                player.getWorld()
                                        .dropItemNaturally(player.getLocation(), leftover));
    }

    private static List<ItemStack> takeItems(Inventory inventory, int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item);
                inventory.setItem(slot, null);
            }
        }
        return items;
    }

    private static void putBack(Inventory inventory, int[] slots, List<ItemStack> items) {
        int i = 0;
        for (ItemStack item : items) {
            inventory.setItem(slots[i++], item);
        }
    }

    private static boolean hasSpaceFor(Inventory inventory, List<ItemStack> items) {
        ItemStack[] simulated = inventory.getStorageContents().clone();
        for (ItemStack item : items) {
            if (!simulateAdd(simulated, item)) {
                return false;
            }
        }
        return true;
    }

    private static boolean simulateAdd(ItemStack[] contents, ItemStack item) {
        int remaining = item.getAmount();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack slot = contents[i];
            if (slot != null && slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                int add = Math.min(remaining, slot.getMaxStackSize() - slot.getAmount());
                slot.setAmount(slot.getAmount() + add);
                remaining -= add;
            }
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] == null) {
                ItemStack clone = item.clone();
                clone.setAmount(Math.min(remaining, item.getMaxStackSize()));
                contents[i] = clone;
                remaining -= clone.getAmount();
            }
        }
        return remaining <= 0;
    }

    // --- Cancellation --------------------------------------------------------------------------

    private void cancel(TradeSession session, String messageKey) {
        manager.endSession(session);
        List<ItemStack> itemsA = takeItems(session.inventory, SLOTS_A);
        List<ItemStack> itemsB = takeItems(session.inventory, SLOTS_B);
        var economy = plugin.services().economy;
        economy.deposit(session.playerA, session.moneyA);
        economy.deposit(session.playerB, session.moneyB);
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);
        if (a != null) {
            a.setLevel(a.getLevel() + session.xpLevelsA);
            itemsA.forEach(item -> giveOrDrop(a, item));
            a.sendMessage(plugin.lang().render(a, messageKey));
            a.closeInventory();
        }
        if (b != null) {
            b.setLevel(b.getLevel() + session.xpLevelsB);
            itemsB.forEach(item -> giveOrDrop(b, item));
            b.sendMessage(plugin.lang().render(b, messageKey));
            b.closeInventory();
        }
    }

    private void notifyBoth(TradeSession session, String key) {
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);
        if (a != null) {
            a.sendMessage(plugin.lang().render(a, key));
        }
        if (b != null) {
            b.sendMessage(plugin.lang().render(b, key));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        manager.activeSession(player.getUniqueId())
                .ifPresent(session -> cancel(session, "trade.cancelled.closed"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.activeSession(event.getPlayer().getUniqueId())
                .ifPresent(session -> cancel(session, "trade.cancelled.disconnected"));
    }

    private static boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    private record TradeHolder() implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

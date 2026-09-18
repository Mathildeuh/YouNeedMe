package fr.mathildeuh.youneedme.modules.shop;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.economy.Currency;
import fr.mathildeuh.youneedme.api.shop.ShopCategory;
import fr.mathildeuh.youneedme.api.shop.ShopItem;
import fr.mathildeuh.youneedme.api.shop.ShopService;
import fr.mathildeuh.youneedme.gui.GuiSize;
import fr.mathildeuh.youneedme.util.ItemConfigCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The {@code /shop} chest GUI: browse categories (sized to how many exist), buy with left-click,
 * sell from hand with right-click, buy a stack with shift-click. Every trade reports back through
 * chat (not just the GUI updating) and a sound, and the view refreshes in place afterwards so stock
 * and balance are never stale.
 */
public final class ShopGui implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final YouNeedMe plugin;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey categoryIdKey;

    public ShopGui(YouNeedMe plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "shop-item-id");
        this.categoryIdKey = new NamespacedKey(plugin, "shop-category-id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openCategories(Player player) {
        ShopService shop = plugin.services().shop;
        List<ShopCategory> categories =
                shop.categories().stream()
                        .filter(c -> c.permission() == null || player.hasPermission(c.permission()))
                        .toList();
        plugin.services()
                .economy
                .balance(player.getUniqueId())
                .thenAccept(
                        balance ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () ->
                                                        renderCategories(
                                                                player, categories, balance)));
    }

    private void renderCategories(Player player, List<ShopCategory> categories, double balance) {
        int size = GuiSize.fit(categories.size() + 1);
        int balanceSlot = size - 1;
        Inventory inventory =
                Bukkit.createInventory(new ShopHolder(null), size, Component.text("Shop"));
        for (int i = 0; i < categories.size() && i < balanceSlot; i++) {
            inventory.setItem(i, categoryIcon(player, categories.get(i)));
        }
        inventory.setItem(balanceSlot, balanceIcon(player, balance));
        player.openInventory(inventory);
    }

    public void openCategory(Player player, ShopCategory category) {
        List<ShopItem> items = category.items();
        int size = GuiSize.fit(items.size() + 9);
        int controlRow = size - 9;
        Inventory inventory =
                Bukkit.createInventory(
                        new ShopHolder(category.id()),
                        size,
                        Component.text("Shop: ")
                                .append(MINI_MESSAGE.deserialize(category.displayName())));
        for (int i = 0; i < items.size() && i < controlRow; i++) {
            inventory.setItem(i, itemIcon(player, items.get(i)));
        }
        inventory.setItem(controlRow, backIcon(player));
        player.openInventory(inventory);
    }

    private ItemStack categoryIcon(Player player, ShopCategory category) {
        ItemStack stack = new ItemStack(category.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(MINI_MESSAGE.deserialize(category.displayName())));
        meta.lore(
                List.of(
                        noItalic(
                                plugin.lang()
                                        .render(player, "shop.gui.main.category-lore-click"))));
        meta.getPersistentDataContainer()
                .set(categoryIdKey, PersistentDataType.STRING, category.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack balanceIcon(Player player, double balance) {
        Currency currency = plugin.services().economy.currency("default");
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(
                noItalic(
                        plugin.lang()
                                .render(
                                        player,
                                        "shop.gui.main.balance-line",
                                        Placeholder.unparsed(
                                                "balance", formatNumber(currency, balance)),
                                        Placeholder.unparsed("currency", currency.symbol()))));
        meta.lore(
                List.of(
                        noItalic(
                                plugin.lang()
                                        .render(player, "shop.gui.main.balance-refresh-hint"))));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack backIcon(Player player) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(plugin.lang().render(player, "shop.gui.main.back")));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack itemIcon(Player player, ShopItem item) {
        Currency currency = plugin.services().economy.currency("default");
        ItemStack stack = item.display().clone();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (item.buyPrice() != null) {
            lore.add(
                    noItalic(
                            plugin.lang()
                                    .render(
                                            player,
                                            "shop.gui.item.buy-line",
                                            Placeholder.unparsed(
                                                    "price",
                                                    formatNumber(currency, item.buyPrice())),
                                            Placeholder.unparsed("currency", currency.symbol()))));
        }
        if (item.sellPrice() != null) {
            lore.add(
                    noItalic(
                            plugin.lang()
                                    .render(
                                            player,
                                            "shop.gui.item.sell-line",
                                            Placeholder.unparsed(
                                                    "price",
                                                    formatNumber(currency, item.sellPrice())),
                                            Placeholder.unparsed("currency", currency.symbol()))));
        }
        if (item.stock() != null) {
            lore.add(
                    noItalic(
                            plugin.lang()
                                    .render(
                                            player,
                                            "shop.gui.item.stock-line",
                                            Placeholder.unparsed(
                                                    "stock", String.valueOf(item.stock())))));
        }
        if (item.isBuyable()) {
            lore.add(noItalic(plugin.lang().render(player, "shop.gui.item.left-click")));
            lore.add(noItalic(plugin.lang().render(player, "shop.gui.item.shift-click")));
        }
        if (item.isSellable()) {
            lore.add(noItalic(plugin.lang().render(player, "shop.gui.item.right-click")));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, item.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private static String formatNumber(Currency currency, double amount) {
        return String.format(Locale.ROOT, "%,." + currency.decimalPlaces() + "f", amount);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (holder.categoryId() == null) {
            String categoryId =
                    meta.getPersistentDataContainer().get(categoryIdKey, PersistentDataType.STRING);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            if (categoryId != null) {
                plugin.services()
                        .shop
                        .category(categoryId)
                        .ifPresent(category -> openCategory(player, category));
            } else {
                openCategories(player); // balance icon - refresh
            }
            return;
        }
        String itemId = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (itemId == null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            openCategories(player); // back icon
            return;
        }
        ShopService shop = plugin.services().shop;
        String categoryId = holder.categoryId();
        if (event.getClick() == ClickType.RIGHT) {
            shop.sell(player.getUniqueId(), itemId, 1)
                    .thenAccept(
                            result ->
                                    handleTradeResult(
                                            player, categoryId, itemId, 1, false, result));
        } else {
            int amount = event.isShiftClick() ? 64 : 1;
            shop.buy(player.getUniqueId(), itemId, amount)
                    .thenAccept(
                            result ->
                                    handleTradeResult(
                                            player, categoryId, itemId, amount, true, result));
        }
    }

    private void handleTradeResult(
            Player player,
            String categoryId,
            String itemId,
            int amount,
            boolean buying,
            ShopService.TradeResult result) {
        plugin.scheduler()
                .runGlobal(
                        () -> {
                            String key =
                                    switch (result) {
                                        case SUCCESS ->
                                                buying
                                                        ? "shop.purchase-success"
                                                        : "shop.sale-success";
                                        case SHOP_DISABLED -> "shop.disabled";
                                        case ITEM_NOT_FOUND -> "shop.item-not-found";
                                        case NOT_BUYABLE -> "shop.not-buyable";
                                        case NOT_SELLABLE -> "shop.not-sellable";
                                        case OUT_OF_STOCK -> "shop.out-of-stock";
                                        case INSUFFICIENT_STOCK_REQUESTED ->
                                                "shop.not-enough-stock";
                                        case INSUFFICIENT_FUNDS -> "shop.not-enough-money";
                                        case INSUFFICIENT_ITEMS -> "shop.not-enough-items";
                                        case INVENTORY_FULL -> "shop.inventory-full";
                                    };
                            boolean success = result == ShopService.TradeResult.SUCCESS;
                            player.playSound(
                                    player.getLocation(),
                                    success
                                            ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                                            : Sound.ENTITY_VILLAGER_NO,
                                    1f,
                                    1f);
                            if (success) {
                                var item = plugin.services().shop.item(itemId);
                                Currency currency = plugin.services().economy.currency("default");
                                double unitPrice =
                                        item.map(i -> buying ? i.buyPrice() : i.sellPrice())
                                                .orElse(0.0);
                                String itemName =
                                        item.map(i -> ItemConfigCodec.plainName(i.display()))
                                                .orElse(itemId);
                                player.sendMessage(
                                        plugin.lang()
                                                .render(
                                                        player,
                                                        key,
                                                        Placeholder.unparsed(
                                                                "amount", String.valueOf(amount)),
                                                        Placeholder.unparsed("item", itemName),
                                                        Placeholder.unparsed(
                                                                "price",
                                                                currency.format(
                                                                        unitPrice * amount))));
                            } else {
                                player.sendMessage(plugin.lang().render(player, key));
                            }
                            if (player.getOpenInventory().getTopInventory().getHolder()
                                            instanceof ShopHolder h
                                    && categoryId.equals(h.categoryId())) {
                                plugin.services()
                                        .shop
                                        .category(categoryId)
                                        .ifPresent(category -> openCategory(player, category));
                            }
                        });
    }

    private record ShopHolder(String categoryId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

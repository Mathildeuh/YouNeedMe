package fr.mathildeuh.youneedme.modules.shop;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.shop.ShopCategory;
import fr.mathildeuh.youneedme.api.shop.ShopItem;
import fr.mathildeuh.youneedme.api.shop.ShopService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Minimal-but-real chest GUI for {@code /shop}: browse categories, left-click buys one, right-click
 * sells one from hand.
 */
public final class ShopGui implements Listener {

    private static final Component CATEGORY_TITLE_PREFIX = Component.text("Shop: ");

    private final YouNeedMe plugin;

    public ShopGui(YouNeedMe plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openCategories(Player player) {
        ShopService shop = plugin.services().shop;
        var categories =
                shop.categories().stream()
                        .filter(c -> c.permission() == null || player.hasPermission(c.permission()))
                        .toList();
        Inventory inventory =
                Bukkit.createInventory(new ShopHolder(null), 27, Component.text("Shop"));
        for (int i = 0; i < categories.size() && i < 27; i++) {
            inventory.setItem(i, categoryIcon(categories.get(i)));
        }
        player.openInventory(inventory);
    }

    public void openCategory(Player player, ShopCategory category) {
        Inventory inventory =
                Bukkit.createInventory(
                        new ShopHolder(category.id()),
                        54,
                        CATEGORY_TITLE_PREFIX.append(Component.text(category.displayName())));
        for (int i = 0; i < category.items().size() && i < 54; i++) {
            inventory.setItem(i, itemIcon(category.items().get(i)));
        }
        player.openInventory(inventory);
    }

    private ItemStack categoryIcon(ShopCategory category) {
        ItemStack stack = new ItemStack(category.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(category.displayName(), NamedTextColor.YELLOW));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack itemIcon(ShopItem item) {
        ItemStack stack = item.display().clone();
        ItemMeta meta = stack.getItemMeta();
        java.util.List<Component> lore = new java.util.ArrayList<>();
        if (item.buyPrice() != null) {
            lore.add(Component.text("Buy: " + item.buyPrice(), NamedTextColor.GREEN));
        }
        if (item.sellPrice() != null) {
            lore.add(Component.text("Sell: " + item.sellPrice(), NamedTextColor.RED));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer()
                .set(itemIdKey(), org.bukkit.persistence.PersistentDataType.STRING, item.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private org.bukkit.NamespacedKey itemIdKey() {
        return new org.bukkit.NamespacedKey(plugin, "shop-item-id");
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
        if (holder.categoryId() == null) {
            plugin.services()
                    .shop
                    .category(materialToCategoryId(clicked))
                    .ifPresent(category -> openCategory(player, category));
            return;
        }
        String itemId =
                clicked.getItemMeta()
                        .getPersistentDataContainer()
                        .get(itemIdKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (itemId == null) {
            return;
        }
        ShopService shop = plugin.services().shop;
        if (event.getClick() == ClickType.RIGHT) {
            shop.sell(player.getUniqueId(), itemId, 1);
        } else {
            shop.buy(player.getUniqueId(), itemId, event.isShiftClick() ? 64 : 1);
        }
    }

    private String materialToCategoryId(ItemStack clicked) {
        return plugin.services().shop.categories().stream()
                .filter(c -> c.icon() == clicked.getType())
                .map(ShopCategory::id)
                .findFirst()
                .orElse("");
    }

    private record ShopHolder(String categoryId) implements org.bukkit.inventory.InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

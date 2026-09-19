package fr.mathildeuh.youneedme.modules.shop;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.gui.DialogInputPrompt;
import fr.mathildeuh.youneedme.util.ItemConfigCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
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
import org.jetbrains.annotations.Nullable;

/**
 * A fully in-game shop editor, mirroring {@link fr.mathildeuh.youneedme.modules.kits.KitEditorGui}:
 * items are placed/removed directly in a chest GUI (any item property carries straight through via
 * {@link ItemConfigCodec}), while buy/sell price and stock are stamped onto each item's own
 * persistent data - so they survive being dragged between slots - and edited through a short {@link
 * DialogInputPrompt} chain.
 */
public final class ShopEditorGui implements Listener {

    private static final int ITEM_SLOTS = 45;
    private static final int SLOT_ICON = 45;
    private static final int SLOT_NAME = 46;
    private static final int SLOT_ORDER = 47;
    private static final int SLOT_PERMISSION = 48;
    private static final int SLOT_SAVE = 50;
    private static final int SLOT_DELETE = 51;
    private static final int SLOT_BACK = 53;
    private static final long DELETE_CONFIRM_WINDOW_MILLIS = 5000;

    private record Draft(
            String displayName, Material icon, int order, @Nullable String permission) {}

    private record ListHolder() implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private record CategoryHolder(String categoryId) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private final YouNeedMe plugin;
    private final NamespacedKey buyPriceKey;
    private final NamespacedKey sellPriceKey;
    private final NamespacedKey stockKey;
    private final Map<UUID, List<String>> listOrder = new ConcurrentHashMap<>();
    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingDelete = new ConcurrentHashMap<>();

    public ShopEditorGui(YouNeedMe plugin) {
        this.plugin = plugin;
        this.buyPriceKey = new NamespacedKey(plugin, "shop-editor-buy-price");
        this.sellPriceKey = new NamespacedKey(plugin, "shop-editor-sell-price");
        this.stockKey = new NamespacedKey(plugin, "shop-editor-stock");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openList(Player player) {
        ConfigurationSection categories =
                plugin.configManager().shop().getConfigurationSection("categories");
        List<String> ids =
                categories == null ? List.of() : new ArrayList<>(categories.getKeys(false));
        listOrder.put(player.getUniqueId(), ids);

        Inventory inventory =
                Bukkit.createInventory(new ListHolder(), 54, Component.text("Shop Editor"));
        for (int i = 0; i < ids.size() && i < 53; i++) {
            ConfigurationSection category = categories.getConfigurationSection(ids.get(i));
            inventory.setItem(i, categoryIcon(ids.get(i), category));
        }
        inventory.setItem(53, plain(Material.LIME_DYE, "<green><bold>+ New Category", List.of()));
        player.openInventory(inventory);
    }

    private ItemStack categoryIcon(String id, @Nullable ConfigurationSection category) {
        Material icon =
                category == null
                        ? Material.CHEST
                        : java.util.Objects.requireNonNullElse(
                                Material.matchMaterial(category.getString("icon", "CHEST")),
                                Material.CHEST);
        int itemCount = category == null ? 0 : category.getMapList("items").size();
        String name = category == null ? id : category.getString("display-name", id);
        return plain(icon, name, List.of("ID: " + id, itemCount + " item(s)", "Click to edit"));
    }

    public void openCategoryEditor(Player player, String categoryId) {
        ConfigurationSection category =
                plugin.configManager().shop().getConfigurationSection("categories." + categoryId);
        Draft draft = drafts.computeIfAbsent(categoryId, id -> draftFrom(id, category));

        Inventory inventory =
                Bukkit.createInventory(
                        new CategoryHolder(categoryId),
                        54,
                        Component.text("Edit Category: " + categoryId));
        if (category != null) {
            ConfigurationSection items = category.getConfigurationSection("items");
            if (items != null) {
                int slot = 0;
                for (String itemId : items.getKeys(false)) {
                    if (slot >= ITEM_SLOTS) {
                        break;
                    }
                    ConfigurationSection itemSection = items.getConfigurationSection(itemId);
                    if (itemSection == null) {
                        continue;
                    }
                    if (!itemSection.contains("material")) {
                        itemSection.set("material", itemId);
                    }
                    ItemStack item = ItemConfigCodec.load(itemSection);
                    if (item == null) {
                        continue;
                    }
                    stampPricing(
                            item,
                            itemSection.contains("buy-price")
                                    ? itemSection.getDouble("buy-price")
                                    : null,
                            itemSection.contains("sell-price")
                                    ? itemSection.getDouble("sell-price")
                                    : null,
                            itemSection.getInt("stock", -1));
                    inventory.setItem(slot++, item);
                }
            }
        }
        renderControls(inventory, draft);
        player.openInventory(inventory);
    }

    private Draft draftFrom(String id, @Nullable ConfigurationSection section) {
        if (section == null) {
            return new Draft(id, Material.CHEST, 0, null);
        }
        Material icon =
                java.util.Objects.requireNonNullElse(
                        Material.matchMaterial(section.getString("icon", "CHEST")), Material.CHEST);
        return new Draft(
                section.getString("display-name", id),
                icon,
                section.getInt("order", 0),
                section.contains("permission") ? section.getString("permission") : null);
    }

    private void renderControls(Inventory inventory, Draft draft) {
        inventory.setItem(
                SLOT_ICON,
                plain(
                        draft.icon(),
                        "<white>Category Icon",
                        List.of("Place an item here to change the icon")));
        inventory.setItem(
                SLOT_NAME,
                plain(
                        Material.NAME_TAG,
                        "<white>Display Name",
                        List.of(plainText(draft.displayName()))));
        inventory.setItem(
                SLOT_ORDER,
                plain(
                        Material.HOPPER,
                        "<white>Display Order",
                        List.of(String.valueOf(draft.order()))));
        inventory.setItem(
                SLOT_PERMISSION,
                plain(
                        Material.TRIPWIRE_HOOK,
                        "<white>Permission",
                        List.of(draft.permission() == null ? "none" : draft.permission())));
        inventory.setItem(
                SLOT_SAVE, plain(Material.EMERALD_BLOCK, "<green><bold>Save & Close", List.of()));
        inventory.setItem(
                SLOT_DELETE,
                plain(
                        Material.TNT,
                        "<red><bold>Delete Category",
                        List.of("Click twice to confirm")));
        inventory.setItem(
                SLOT_BACK,
                plain(Material.BARRIER, "<gray>Back", List.of("Discards unsaved changes")));
    }

    private void stampPricing(
            ItemStack item, @Nullable Double buyPrice, @Nullable Double sellPrice, int stock) {
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (buyPrice != null) {
            pdc.set(buyPriceKey, PersistentDataType.DOUBLE, buyPrice);
        }
        if (sellPrice != null) {
            pdc.set(sellPriceKey, PersistentDataType.DOUBLE, sellPrice);
        }
        pdc.set(stockKey, PersistentDataType.INTEGER, stock);
        List<Component> existingLore = meta.lore();
        List<Component> lore =
                existingLore != null ? new ArrayList<>(existingLore) : new ArrayList<>();
        lore.removeIf(
                line ->
                        PlainTextComponentSerializer.plainText()
                                .serialize(line)
                                .startsWith("Buy: "));
        lore.add(
                Component.text(
                        "Buy: "
                                + (buyPrice == null ? "-" : buyPrice)
                                + "  Sell: "
                                + (sellPrice == null ? "-" : sellPrice)
                                + "  Stock: "
                                + (stock < 0 ? "unlimited" : stock),
                        NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private static String plainText(String miniMessage) {
        return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private ItemStack plain(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name));
        meta.lore(
                lore.stream()
                        .<Component>map(line -> Component.text(line, NamedTextColor.GRAY))
                        .toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof ListHolder) {
            onListClick(event, player);
        } else if (event.getInventory().getHolder() instanceof CategoryHolder holder) {
            onCategoryClick(event, player, holder.categoryId());
        }
    }

    private void onListClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 53) {
            DialogInputPrompt.open(
                    player,
                    "New Category",
                    "Category ID",
                    "",
                    id -> {
                        String categoryId =
                                id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                        if (!categoryId.isBlank()) {
                            drafts.put(categoryId, new Draft(categoryId, Material.CHEST, 0, null));
                            plugin.scheduler()
                                    .runGlobal(() -> openCategoryEditor(player, categoryId));
                        }
                    });
            return;
        }
        List<String> ids = listOrder.getOrDefault(player.getUniqueId(), List.of());
        if (slot >= 0 && slot < ids.size()) {
            openCategoryEditor(player, ids.get(slot));
        }
    }

    private void onCategoryClick(InventoryClickEvent event, Player player, String categoryId) {
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < ITEM_SLOTS) {
            onItemSlotClick(event, player, categoryId, slot);
            return;
        }
        if (slot == SLOT_ICON) {
            onIconSlotClick(event, player, categoryId);
            return;
        }
        if (slot < 0 || slot >= 54) {
            return;
        }
        event.setCancelled(true);
        Draft draft =
                drafts.computeIfAbsent(
                        categoryId,
                        id ->
                                draftFrom(
                                        id,
                                        plugin.configManager()
                                                .shop()
                                                .getConfigurationSection("categories." + id)));
        switch (slot) {
            case SLOT_NAME ->
                    DialogInputPrompt.open(
                            player,
                            "Display Name",
                            "MiniMessage-formatted name",
                            draft.displayName(),
                            value -> {
                                drafts.put(
                                        categoryId,
                                        new Draft(
                                                value,
                                                draft.icon(),
                                                draft.order(),
                                                draft.permission()));
                                plugin.scheduler()
                                        .runGlobal(() -> openCategoryEditor(player, categoryId));
                            });
            case SLOT_ORDER ->
                    DialogInputPrompt.open(
                            player,
                            "Display Order",
                            "Lower numbers show first",
                            String.valueOf(draft.order()),
                            value -> {
                                int order = parseIntOrDefault(value, draft.order());
                                drafts.put(
                                        categoryId,
                                        new Draft(
                                                draft.displayName(),
                                                draft.icon(),
                                                order,
                                                draft.permission()));
                                plugin.scheduler()
                                        .runGlobal(() -> openCategoryEditor(player, categoryId));
                            });
            case SLOT_PERMISSION ->
                    DialogInputPrompt.open(
                            player,
                            "Permission",
                            "Permission node ('none' to clear)",
                            draft.permission() == null ? "" : draft.permission(),
                            value -> {
                                String perm =
                                        value.isBlank() || "none".equalsIgnoreCase(value)
                                                ? null
                                                : value.trim();
                                drafts.put(
                                        categoryId,
                                        new Draft(
                                                draft.displayName(),
                                                draft.icon(),
                                                draft.order(),
                                                perm));
                                plugin.scheduler()
                                        .runGlobal(() -> openCategoryEditor(player, categoryId));
                            });
            case SLOT_SAVE -> save(player, categoryId, event.getInventory());
            case SLOT_DELETE -> delete(player, categoryId);
            case SLOT_BACK -> {
                drafts.remove(categoryId);
                openList(player);
            }
            default -> {}
        }
    }

    private void onIconSlotClick(InventoryClickEvent event, Player player, String categoryId) {
        ItemStack cursor = event.getCursor();
        if (cursor.getType().isAir()) {
            event.setCancelled(true);
            return;
        }
        Material newIcon = cursor.getType();
        event.setCancelled(true);
        Draft draft =
                drafts.getOrDefault(categoryId, new Draft(categoryId, Material.CHEST, 0, null));
        drafts.put(
                categoryId,
                new Draft(draft.displayName(), newIcon, draft.order(), draft.permission()));
        plugin.scheduler().runGlobal(() -> openCategoryEditor(player, categoryId));
    }

    /** Empty cursor + occupied slot = "open properties" rather than the normal pick-up gesture. */
    private void onItemSlotClick(
            InventoryClickEvent event, Player player, String categoryId, int slot) {
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        boolean cursorEmpty = cursor.getType().isAir();
        boolean slotOccupied = clicked != null && !clicked.getType().isAir();
        if (!cursorEmpty || !slotOccupied) {
            return; // normal placement/pickup/swap - let Bukkit handle it uncancelled
        }
        event.setCancelled(true);
        editItemPricing(player, categoryId, event.getInventory(), slot, clicked);
    }

    private void editItemPricing(
            Player player, String categoryId, Inventory inventory, int slot, ItemStack item) {
        Double currentBuy = readDouble(item, buyPriceKey);
        DialogInputPrompt.open(
                player,
                "Buy Price",
                "Buy price ('none' to disable buying)",
                currentBuy == null ? "none" : String.valueOf(currentBuy),
                buyText ->
                        promptSellPrice(
                                player,
                                categoryId,
                                inventory,
                                slot,
                                item,
                                parsePriceOrNull(buyText)));
    }

    private void promptSellPrice(
            Player player,
            String categoryId,
            Inventory inventory,
            int slot,
            ItemStack item,
            @Nullable Double buy) {
        Double currentSell = readDouble(item, sellPriceKey);
        plugin.scheduler()
                .runGlobal(
                        () ->
                                DialogInputPrompt.open(
                                        player,
                                        "Sell Price",
                                        "Sell price ('none' to disable selling)",
                                        currentSell == null ? "none" : String.valueOf(currentSell),
                                        sellText ->
                                                promptStock(
                                                        player,
                                                        categoryId,
                                                        inventory,
                                                        slot,
                                                        item,
                                                        buy,
                                                        parsePriceOrNull(sellText))));
    }

    private void promptStock(
            Player player,
            String categoryId,
            Inventory inventory,
            int slot,
            ItemStack item,
            @Nullable Double buy,
            @Nullable Double sell) {
        int currentStock = readInt(item, stockKey, -1);
        plugin.scheduler()
                .runGlobal(
                        () ->
                                DialogInputPrompt.open(
                                        player,
                                        "Stock",
                                        "Stock ('none' for unlimited)",
                                        currentStock < 0 ? "none" : String.valueOf(currentStock),
                                        stockText ->
                                                applyPricing(
                                                        player,
                                                        categoryId,
                                                        inventory,
                                                        slot,
                                                        item,
                                                        buy,
                                                        sell,
                                                        parseStock(stockText))));
    }

    private int parseStock(String stockText) {
        return "none".equalsIgnoreCase(stockText.trim()) ? -1 : parseIntOrDefault(stockText, -1);
    }

    private void applyPricing(
            Player player,
            String categoryId,
            Inventory inventory,
            int slot,
            ItemStack item,
            @Nullable Double buy,
            @Nullable Double sell,
            int stock) {
        stampPricing(item, buy, sell, stock);
        plugin.scheduler()
                .runGlobal(
                        () -> {
                            inventory.setItem(slot, item);
                            openCategoryEditor(player, categoryId);
                        });
    }

    private @Nullable Double readDouble(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        return meta == null
                ? null
                : meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
    }

    private int readInt(ItemStack item, NamespacedKey key, int fallback) {
        ItemMeta meta = item.getItemMeta();
        Integer value =
                meta == null
                        ? null
                        : meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? fallback : value;
    }

    private static @Nullable Double parsePriceOrNull(String raw) {
        if (raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save(Player player, String categoryId, Inventory inventory) {
        Map<String, Object> itemsMap = new LinkedHashMap<>();
        int anonymousIndex = 0;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<String, Object> itemMap = ItemConfigCodec.toMap(item);
            Double buy = readDouble(item, buyPriceKey);
            Double sell = readDouble(item, sellPriceKey);
            int stock = readInt(item, stockKey, -1);
            if (buy != null) {
                itemMap.put("buy-price", buy);
            }
            if (sell != null) {
                itemMap.put("sell-price", sell);
            }
            if (stock >= 0) {
                itemMap.put("stock", stock);
            }
            String itemId =
                    ItemConfigCodec.plainName(item)
                            .toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9_-]", "-");
            if (itemId.isBlank() || itemsMap.containsKey(itemId)) {
                itemId = item.getType().name().toLowerCase(Locale.ROOT) + "-" + anonymousIndex++;
            }
            itemsMap.put(itemId, itemMap);
        }

        Draft draft =
                drafts.getOrDefault(categoryId, new Draft(categoryId, Material.CHEST, 0, null));
        var shopConfig = plugin.configManager().shop();
        ConfigurationSection categories = shopConfig.getConfigurationSection("categories");
        if (categories == null) {
            categories = shopConfig.createSection("categories");
        }
        ConfigurationSection thisCategory = categories.createSection(categoryId);
        thisCategory.set("display-name", draft.displayName());
        thisCategory.set("icon", draft.icon().name());
        thisCategory.set("order", draft.order());
        if (draft.permission() != null) {
            thisCategory.set("permission", draft.permission());
        }
        ConfigurationSection itemsSection = thisCategory.createSection("items");
        itemsMap.forEach((id, value) -> itemsSection.set(id, value));
        plugin.configManager().saveShop(shopConfig);

        plugin.services().shop.reload();
        drafts.remove(categoryId);

        player.sendMessage(
                Component.text("Saved category '" + categoryId + "'.", NamedTextColor.GREEN));
        player.closeInventory();
    }

    private void delete(Player player, String categoryId) {
        long now = System.currentTimeMillis();
        Long previous = pendingDelete.get(player.getUniqueId());
        if (previous == null || now - previous > DELETE_CONFIRM_WINDOW_MILLIS) {
            pendingDelete.put(player.getUniqueId(), now);
            player.sendMessage(
                    Component.text(
                            "Click delete again within 5 seconds to confirm.", NamedTextColor.RED));
            return;
        }
        pendingDelete.remove(player.getUniqueId());

        var shopConfig = plugin.configManager().shop();
        ConfigurationSection categories = shopConfig.getConfigurationSection("categories");
        if (categories != null) {
            categories.set(categoryId, null);
            plugin.configManager().saveShop(shopConfig);
        }
        plugin.services().shop.reload();
        drafts.remove(categoryId);

        player.sendMessage(
                Component.text("Deleted category '" + categoryId + "'.", NamedTextColor.RED));
        openList(player);
    }
}

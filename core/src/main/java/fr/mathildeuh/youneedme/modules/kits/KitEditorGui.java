package fr.mathildeuh.youneedme.modules.kits;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.kits.Kit;
import fr.mathildeuh.youneedme.gui.DialogInputPrompt;
import fr.mathildeuh.youneedme.util.ItemConfigCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * A fully in-game kit editor: item contents are edited by physically placing/removing items in a
 * chest GUI (so any item property an admin can give an item in survival/creative - enchantments,
 * lore, custom name, unbreakable - carries straight through to the saved kit, no YAML editing
 * required), while the kit's other properties are edited through {@link DialogInputPrompt}.
 */
public final class KitEditorGui implements Listener {

    private static final int ITEM_SLOTS = 45;
    private static final int SLOT_NAME = 45;
    private static final int SLOT_PERMISSION = 46;
    private static final int SLOT_COOLDOWN = 47;
    private static final int SLOT_ONE_TIME = 48;
    private static final int SLOT_MAX_CLAIMS = 49;
    private static final int SLOT_SAVE = 50;
    private static final int SLOT_DELETE = 51;
    private static final int SLOT_BACK = 53;
    private static final long DELETE_CONFIRM_WINDOW_MILLIS = 5000;

    private record Draft(
            String displayName,
            @Nullable String permission,
            long cooldownSeconds,
            boolean oneTime,
            @Nullable Integer maxClaims) {}

    private record ListHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private record EditorHolder(String kitId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private final YouNeedMe plugin;
    private final Map<UUID, List<String>> listOrder = new ConcurrentHashMap<>();
    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingDelete = new ConcurrentHashMap<>();

    public KitEditorGui(YouNeedMe plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openList(Player player) {
        ConfigurationSection kits = plugin.configManager().kits().getConfigurationSection("kits");
        List<String> ids = kits == null ? List.of() : new ArrayList<>(kits.getKeys(false));
        listOrder.put(player.getUniqueId(), ids);

        Inventory inventory =
                Bukkit.createInventory(new ListHolder(), 54, Component.text("Kit Editor"));
        for (int i = 0; i < ids.size() && i < 53; i++) {
            String id = ids.get(i);
            ConfigurationSection kitSection = kits.getConfigurationSection(id);
            inventory.setItem(i, listIcon(id, kitSection));
        }
        inventory.setItem(53, simple(Material.LIME_DYE, "<green><bold>+ New Kit", List.of()));
        player.openInventory(inventory);
    }

    private ItemStack listIcon(String id, @Nullable ConfigurationSection kitSection) {
        ItemStack icon = firstConfiguredItem(kitSection);
        ItemMeta meta = icon.getItemMeta();
        String displayName = kitSection == null ? id : kitSection.getString("display-name", id);
        meta.displayName(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(displayName));
        meta.lore(
                List.of(
                        Component.text("ID: " + id, NamedTextColor.GRAY),
                        Component.text("Click to edit", NamedTextColor.YELLOW)));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack firstConfiguredItem(@Nullable ConfigurationSection kitSection) {
        if (kitSection != null) {
            for (var raw : kitSection.getMapList("items")) {
                ItemStack item =
                        ItemConfigCodec.load(new MemoryConfiguration().createSection("item", raw));
                if (item != null) {
                    return item.clone();
                }
            }
        }
        return new ItemStack(Material.CHEST);
    }

    public void openEditor(Player player, String kitId) {
        ConfigurationSection kitSection =
                plugin.configManager().kits().getConfigurationSection("kits." + kitId);
        Draft draft = drafts.computeIfAbsent(kitId, id -> draftFrom(id, kitSection));

        Inventory inventory =
                Bukkit.createInventory(
                        new EditorHolder(kitId), 54, Component.text("Edit Kit: " + kitId));
        if (kitSection != null) {
            int slot = 0;
            for (var raw : kitSection.getMapList("items")) {
                if (slot >= ITEM_SLOTS) {
                    break;
                }
                ItemStack item =
                        ItemConfigCodec.load(new MemoryConfiguration().createSection("item", raw));
                if (item != null) {
                    inventory.setItem(slot++, item);
                }
            }
        }
        renderControls(inventory, draft);
        player.openInventory(inventory);
    }

    private Draft draftFrom(String id, @Nullable ConfigurationSection section) {
        if (section == null) {
            return new Draft(id, null, 0, false, null);
        }
        return new Draft(
                section.getString("display-name", id),
                section.contains("permission") ? section.getString("permission") : null,
                section.getLong("cooldown-seconds", 0),
                section.getBoolean("one-time", false),
                section.contains("max-claims") ? section.getInt("max-claims") : null);
    }

    private void renderControls(Inventory inventory, Draft draft) {
        inventory.setItem(
                SLOT_NAME,
                simple(
                        Material.NAME_TAG,
                        "<white>Display Name",
                        List.of(plain(draft.displayName()))));
        inventory.setItem(
                SLOT_PERMISSION,
                simple(
                        Material.TRIPWIRE_HOOK,
                        "<white>Permission",
                        List.of(draft.permission() == null ? "none" : draft.permission())));
        inventory.setItem(
                SLOT_COOLDOWN,
                simple(
                        Material.CLOCK,
                        "<white>Cooldown (seconds)",
                        List.of(String.valueOf(draft.cooldownSeconds()))));
        inventory.setItem(
                SLOT_ONE_TIME,
                simple(
                        draft.oneTime() ? Material.LIME_DYE : Material.GRAY_DYE,
                        "<white>One-time claim",
                        List.of(
                                draft.oneTime()
                                        ? "Yes - click to allow repeats"
                                        : "No - click to make one-time")));
        inventory.setItem(
                SLOT_MAX_CLAIMS,
                simple(
                        Material.HOPPER,
                        "<white>Max Claims",
                        List.of(
                                draft.maxClaims() == null
                                        ? "none"
                                        : String.valueOf(draft.maxClaims()))));
        inventory.setItem(
                SLOT_SAVE, simple(Material.EMERALD_BLOCK, "<green><bold>Save & Close", List.of()));
        inventory.setItem(
                SLOT_DELETE,
                simple(Material.TNT, "<red><bold>Delete Kit", List.of("Click twice to confirm")));
        inventory.setItem(
                SLOT_BACK,
                simple(Material.BARRIER, "<gray>Back", List.of("Saves and returns to the list")));
    }

    private static String plain(String miniMessage) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize(miniMessage));
    }

    private ItemStack simple(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(
                lore.stream()
                        .map(Component::text)
                        .map(c -> (Component) c.color(NamedTextColor.GRAY))
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
        } else if (event.getInventory().getHolder() instanceof EditorHolder holder) {
            onEditorClick(event, player, holder.kitId());
        }
    }

    private void onListClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 53) {
            DialogInputPrompt.open(
                    player,
                    "New Kit",
                    "Kit ID",
                    "",
                    id -> {
                        String kitId =
                                id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                        if (!kitId.isBlank()) {
                            drafts.put(kitId, new Draft(kitId, null, 0, false, null));
                            plugin.scheduler().runGlobal(() -> openEditor(player, kitId));
                        }
                    });
            return;
        }
        List<String> ids = listOrder.getOrDefault(player.getUniqueId(), List.of());
        if (slot >= 0 && slot < ids.size()) {
            openEditor(player, ids.get(slot));
        }
    }

    private void onEditorClick(InventoryClickEvent event, Player player, String kitId) {
        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        // Only the control row (45-53) in the TOP inventory is off-limits. Item slots (0-44) and
        // the player's own inventory (rawSlot >= topSize) must stay uncancelled for normal
        // pickup/drag/shift-click - cancelling rawSlot >= ITEM_SLOTS used to also cancel every
        // click in the player's own inventory, making it impossible to ever pick an item up to
        // place it in the kit.
        if (slot < ITEM_SLOTS || slot >= topSize) {
            return;
        }
        event.setCancelled(true);
        Draft draft =
                drafts.computeIfAbsent(
                        kitId,
                        id ->
                                draftFrom(
                                        id,
                                        plugin.configManager()
                                                .kits()
                                                .getConfigurationSection("kits." + id)));
        switch (slot) {
            case SLOT_NAME ->
                    DialogInputPrompt.open(
                            player,
                            "Display Name",
                            "MiniMessage-formatted name",
                            draft.displayName(),
                            value -> {
                                drafts.put(
                                        kitId,
                                        new Draft(
                                                value,
                                                draft.permission(),
                                                draft.cooldownSeconds(),
                                                draft.oneTime(),
                                                draft.maxClaims()));
                                plugin.scheduler().runGlobal(() -> openEditor(player, kitId));
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
                                        kitId,
                                        new Draft(
                                                draft.displayName(),
                                                perm,
                                                draft.cooldownSeconds(),
                                                draft.oneTime(),
                                                draft.maxClaims()));
                                plugin.scheduler().runGlobal(() -> openEditor(player, kitId));
                            });
            case SLOT_COOLDOWN ->
                    DialogInputPrompt.open(
                            player,
                            "Cooldown",
                            "Cooldown in seconds",
                            String.valueOf(draft.cooldownSeconds()),
                            value -> {
                                long seconds = parseLongOrDefault(value, draft.cooldownSeconds());
                                drafts.put(
                                        kitId,
                                        new Draft(
                                                draft.displayName(),
                                                draft.permission(),
                                                Math.max(0, seconds),
                                                draft.oneTime(),
                                                draft.maxClaims()));
                                plugin.scheduler().runGlobal(() -> openEditor(player, kitId));
                            });
            case SLOT_ONE_TIME -> {
                drafts.put(
                        kitId,
                        new Draft(
                                draft.displayName(),
                                draft.permission(),
                                draft.cooldownSeconds(),
                                !draft.oneTime(),
                                draft.maxClaims()));
                openEditor(player, kitId);
            }
            case SLOT_MAX_CLAIMS ->
                    DialogInputPrompt.open(
                            player,
                            "Max Claims",
                            "Max claims ('none' for unlimited)",
                            draft.maxClaims() == null ? "" : String.valueOf(draft.maxClaims()),
                            value -> {
                                Integer max =
                                        value.isBlank() || "none".equalsIgnoreCase(value)
                                                ? null
                                                : parseIntOrNull(value);
                                drafts.put(
                                        kitId,
                                        new Draft(
                                                draft.displayName(),
                                                draft.permission(),
                                                draft.cooldownSeconds(),
                                                draft.oneTime(),
                                                max));
                                plugin.scheduler().runGlobal(() -> openEditor(player, kitId));
                            });
            case SLOT_SAVE, SLOT_BACK -> save(player, kitId, event.getInventory());
            case SLOT_DELETE -> delete(player, kitId);
            default -> {}
        }
    }

    private static long parseLongOrDefault(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static @Nullable Integer parseIntOrNull(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void save(Player player, String kitId, Inventory inventory) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.add(ItemConfigCodec.toMap(item));
            }
        }
        Draft draft = drafts.getOrDefault(kitId, new Draft(kitId, null, 0, false, null));

        var kitsConfig = plugin.configManager().kits();
        ConfigurationSection kitsSection = kitsConfig.getConfigurationSection("kits");
        if (kitsSection == null) {
            kitsSection = kitsConfig.createSection("kits");
        }
        ConfigurationSection thisKit = kitsSection.createSection(kitId);
        thisKit.set("display-name", draft.displayName());
        if (draft.permission() != null) {
            thisKit.set("permission", draft.permission());
        }
        thisKit.set("cooldown-seconds", draft.cooldownSeconds());
        thisKit.set("one-time", draft.oneTime());
        if (draft.maxClaims() != null) {
            thisKit.set("max-claims", draft.maxClaims());
        }
        thisKit.set("items", items);
        plugin.configManager().saveKits(kitsConfig);

        List<Kit> reloaded = KitLoader.load(kitsConfig);
        plugin.services().kits.reloadFrom(reloaded);
        drafts.remove(kitId);

        player.sendMessage(Component.text("Saved kit '" + kitId + "'.", NamedTextColor.GREEN));
        player.closeInventory();
    }

    private void delete(Player player, String kitId) {
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

        var kitsConfig = plugin.configManager().kits();
        ConfigurationSection kitsSection = kitsConfig.getConfigurationSection("kits");
        if (kitsSection != null) {
            kitsSection.set(kitId, null);
            plugin.configManager().saveKits(kitsConfig);
        }
        plugin.services().kits.unregister(kitId);
        drafts.remove(kitId);

        player.sendMessage(Component.text("Deleted kit '" + kitId + "'.", NamedTextColor.RED));
        openList(player);
    }
}

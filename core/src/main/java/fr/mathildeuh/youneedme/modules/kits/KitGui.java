package fr.mathildeuh.youneedme.modules.kits;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.kits.Kit;
import fr.mathildeuh.youneedme.api.kits.KitClaimState;
import fr.mathildeuh.youneedme.api.kits.KitService;
import fr.mathildeuh.youneedme.gui.GuiSize;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
 * The {@code /kit} browser: every kit the player can see, sized to how many there are (or
 * paginated, 45 per page, once there are too many for one screen), colored by whether it's
 * available/on cooldown/used up, claimable with a single click. Reuses the same {@code
 * kit.gui.*}-keyed lang templates {@code /kits edit} and the text {@code /kit} subcommands already
 * shared, so wording stays consistent everywhere a kit shows up.
 */
public final class KitGui implements Listener {

    private static final int PAGE_SIZE = 45;

    private final YouNeedMe plugin;
    private final NamespacedKey kitIdKey;

    public KitGui(YouNeedMe plugin) {
        this.plugin = plugin;
        this.kitIdKey = new NamespacedKey(plugin, "kit-id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        KitService kits = plugin.services().kits;
        List<Kit> visible =
                kits.kits().stream()
                        .filter(k -> k.permission() == null || player.hasPermission(k.permission()))
                        .sorted(Comparator.comparing(Kit::id))
                        .toList();
        List<CompletableFuture<Entry>> futures =
                visible.stream().map(kit -> buildEntry(player, kits, kit)).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenAccept(
                        v -> {
                            List<Entry> entries =
                                    futures.stream().map(CompletableFuture::join).toList();
                            plugin.scheduler().runGlobal(() -> render(player, entries, page));
                        });
    }

    private CompletableFuture<Entry> buildEntry(Player player, KitService kits, Kit kit) {
        return kits.claimState(player.getUniqueId(), kit.id())
                .thenCompose(
                        state ->
                                kits.cooldownRemaining(player.getUniqueId(), kit.id())
                                        .thenApply(remaining -> new Entry(kit, state, remaining)));
    }

    private void render(Player player, List<Entry> entries, int page) {
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clampedPage = Math.max(1, Math.min(page, totalPages));
        boolean paginated = totalPages > 1;
        int size = paginated ? 54 : GuiSize.fit(entries.size());
        int contentSlots = paginated ? PAGE_SIZE : size;

        Inventory inventory =
                Bukkit.createInventory(
                        new Holder(clampedPage),
                        size,
                        plugin.lang()
                                .render(
                                        player,
                                        "kit.gui.title",
                                        Placeholder.unparsed("page", String.valueOf(clampedPage)),
                                        Placeholder.unparsed("total", String.valueOf(totalPages))));

        if (entries.isEmpty()) {
            inventory.setItem(size / 2, emptyIcon(player));
        } else {
            int start = (clampedPage - 1) * PAGE_SIZE;
            int end = Math.min(entries.size(), start + contentSlots);
            for (int i = start; i < end; i++) {
                inventory.setItem(i - start, kitIcon(player, entries.get(i)));
            }
        }

        if (paginated) {
            if (clampedPage > 1) {
                inventory.setItem(45, navIcon(player, "kit.gui.item.nav.prev.name"));
            }
            if (clampedPage < totalPages) {
                inventory.setItem(53, navIcon(player, "kit.gui.item.nav.next.name"));
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack emptyIcon(Player player) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(plugin.lang().render(player, "kit.gui.item.empty.name")));
        meta.lore(List.of(noItalic(plugin.lang().render(player, "kit.gui.item.empty.lore1"))));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack navIcon(Player player, String nameKey) {
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(plugin.lang().render(player, nameKey)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack kitIcon(Player player, Entry entry) {
        Kit kit = entry.kit();
        boolean usedUp =
                (kit.oneTime() && entry.state().claimCount() > 0)
                        || (kit.maxClaims() != null
                                && entry.state().claimCount() >= kit.maxClaims());
        boolean onCooldown = !usedUp && entry.cooldownRemaining() > 0;
        boolean claimable = !usedUp && !onCooldown;

        ItemStack stack =
                kit.items().stream()
                        .filter(i -> i != null && !i.getType().isAir())
                        .findFirst()
                        .map(ItemStack::clone)
                        .orElseGet(() -> new ItemStack(Material.CHEST));
        ItemMeta meta = stack.getItemMeta();
        String nameKey =
                usedUp
                        ? "kit.gui.item.kit.name.used"
                        : onCooldown
                                ? "kit.gui.item.kit.name.cooldown"
                                : "kit.gui.item.kit.name.available";
        meta.displayName(
                noItalic(
                        plugin.lang()
                                .render(
                                        player,
                                        nameKey,
                                        Placeholder.unparsed("kit", kit.displayName()))));

        List<Component> lore = new ArrayList<>();
        lore.add(
                noItalic(
                        plugin.lang()
                                .render(
                                        player,
                                        "kit.gui.item.kit.lore.items",
                                        Placeholder.unparsed(
                                                "count", String.valueOf(kit.items().size())))));
        if (kit.hasCooldown()) {
            lore.add(
                    noItalic(
                            plugin.lang()
                                    .render(
                                            player,
                                            "kit.gui.item.kit.lore.cooldown",
                                            Placeholder.unparsed(
                                                    "time",
                                                    TimeParser.format(
                                                            kit.cooldownSeconds() * 1000)))));
        }
        if (onCooldown) {
            lore.add(
                    noItalic(
                            plugin.lang()
                                    .render(
                                            player,
                                            "kit.gui.item.kit.lore.ready_in",
                                            Placeholder.unparsed(
                                                    "time",
                                                    TimeParser.format(
                                                            entry.cooldownRemaining() * 1000)))));
        }
        if (kit.maxClaims() != null) {
            lore.add(
                    noItalic(
                            plugin.lang()
                                    .render(
                                            player,
                                            "kit.gui.item.kit.lore.claims",
                                            Placeholder.unparsed(
                                                    "count",
                                                    String.valueOf(entry.state().claimCount())),
                                            Placeholder.unparsed(
                                                    "max", String.valueOf(kit.maxClaims())))));
        }
        if (claimable) {
            lore.add(
                    noItalic(plugin.lang().render(player, "kit.gui.item.kit.lore.click_to_claim")));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(kitIdKey, PersistentDataType.STRING, kit.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45 && holder.page() > 1) {
            open(player, holder.page() - 1);
            return;
        }
        if (slot == 53) {
            open(player, holder.page() + 1); // harmless if there's no next page - render() clamps
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) {
            return;
        }
        String kitId =
                clicked.getItemMeta()
                        .getPersistentDataContainer()
                        .get(kitIdKey, PersistentDataType.STRING);
        if (kitId == null) {
            return;
        }
        int page = holder.page();
        plugin.services()
                .kits
                .claim(player.getUniqueId(), kitId)
                .thenAccept(result -> handleClaimResult(player, page, kitId, result));
    }

    private void handleClaimResult(
            Player player, int page, String kitId, KitService.ClaimResult result) {
        plugin.scheduler()
                .runGlobal(
                        () -> {
                            boolean success = result == KitService.ClaimResult.SUCCESS;
                            player.playSound(
                                    player.getLocation(),
                                    success
                                            ? Sound.ENTITY_PLAYER_LEVELUP
                                            : Sound.ENTITY_VILLAGER_NO,
                                    1f,
                                    1f);
                            switch (result) {
                                case SUCCESS ->
                                        player.sendMessage(
                                                plugin.lang()
                                                        .render(
                                                                player,
                                                                "kit.claim.success",
                                                                Placeholder.unparsed(
                                                                        "kit", kitId)));
                                case KIT_NOT_FOUND ->
                                        player.sendMessage(
                                                plugin.lang()
                                                        .render(
                                                                player,
                                                                "kit.not_found",
                                                                Placeholder.unparsed(
                                                                        "kit", kitId)));
                                case NO_PERMISSION ->
                                        player.sendMessage(
                                                plugin.lang()
                                                        .render(
                                                                player,
                                                                "kit.no_permission",
                                                                Placeholder.unparsed(
                                                                        "kit", kitId)));
                                case ALREADY_CLAIMED_ONE_TIME ->
                                        player.sendMessage(
                                                plugin.lang()
                                                        .render(
                                                                player,
                                                                "kit.one_time_used",
                                                                Placeholder.unparsed(
                                                                        "kit", kitId)));
                                case MAX_CLAIMS_REACHED ->
                                        plugin.services()
                                                .kits
                                                .kit(kitId)
                                                .ifPresent(
                                                        k ->
                                                                sendMaxClaimsMessage(
                                                                        player,
                                                                        kitId,
                                                                        k.maxClaims()));
                                case ON_COOLDOWN ->
                                        plugin.services()
                                                .kits
                                                .cooldownRemaining(player.getUniqueId(), kitId)
                                                .thenAccept(
                                                        remaining ->
                                                                sendCooldownMessage(
                                                                        player, kitId, remaining));
                                case INVENTORY_FULL ->
                                        player.sendMessage(
                                                plugin.lang()
                                                        .render(player, "shop.inventory-full"));
                                default -> {}
                            }
                            open(player, page);
                        });
    }

    private void sendMaxClaimsMessage(Player player, String kitId, @Nullable Integer maxClaims) {
        player.sendMessage(
                plugin.lang()
                        .render(
                                player,
                                "kit.max_claims_reached",
                                Placeholder.unparsed("max", String.valueOf(maxClaims)),
                                Placeholder.unparsed("kit", kitId)));
    }

    private void sendCooldownMessage(Player player, String kitId, long remainingSeconds) {
        plugin.scheduler()
                .runGlobal(
                        () ->
                                player.sendMessage(
                                        plugin.lang()
                                                .render(
                                                        player,
                                                        "kit.cooldown_active",
                                                        Placeholder.unparsed("kit", kitId),
                                                        Placeholder.unparsed(
                                                                "time",
                                                                TimeParser.format(
                                                                        remainingSeconds
                                                                                * 1000)))));
    }

    private record Entry(Kit kit, KitClaimState state, long cooldownRemaining) {}

    private record Holder(int page) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

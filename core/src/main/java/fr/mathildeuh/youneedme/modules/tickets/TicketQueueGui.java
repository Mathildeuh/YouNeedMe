package fr.mathildeuh.youneedme.modules.tickets;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.tickets.Ticket;
import fr.mathildeuh.youneedme.api.tickets.TicketService;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
 * The staff ticket queue: browse open/claimed tickets, left-click to claim, right-click to close.
 * Reading/replying to a ticket's actual thread stays a chat command ({@code /ticket view <id>}) - a
 * scrolling conversation doesn't fit an inventory grid.
 */
public final class TicketQueueGui implements Listener {

    private static final int PAGE_SIZE = 45;

    private final YouNeedMe plugin;
    private final NamespacedKey ticketIdKey;

    public TicketQueueGui(YouNeedMe plugin) {
        this.plugin = plugin;
        this.ticketIdKey = new NamespacedKey(plugin, "ticket-id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        plugin.services()
                .tickets
                .listOpen()
                .thenAccept(
                        tickets ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    int from = (page - 1) * PAGE_SIZE;
                                                    List<Ticket> pageItems =
                                                            tickets.subList(
                                                                    Math.min(from, tickets.size()),
                                                                    Math.min(
                                                                            from + PAGE_SIZE,
                                                                            tickets.size()));
                                                    Inventory inventory =
                                                            Bukkit.createInventory(
                                                                    new QueueHolder(page),
                                                                    54,
                                                                    Component.text(
                                                                            "Ticket Queue - Page "
                                                                                    + page));
                                                    for (int i = 0; i < pageItems.size(); i++) {
                                                        inventory.setItem(
                                                                i, ticketIcon(pageItems.get(i)));
                                                    }
                                                    inventory.setItem(
                                                            45, navIcon("<white>Previous Page"));
                                                    inventory.setItem(
                                                            49, navIcon("<gray>Close Menu"));
                                                    inventory.setItem(
                                                            53, navIcon("<white>Next Page"));
                                                    player.openInventory(inventory);
                                                }));
    }

    private ItemStack ticketIcon(Ticket ticket) {
        Material material =
                ticket.status() == Ticket.Status.CLAIMED ? Material.WRITABLE_BOOK : Material.PAPER;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(
                MiniMessage.miniMessage()
                        .deserialize(
                                "<yellow>Ticket #"
                                        + ticket.id()
                                        + " <gray>- "
                                        + ticket.playerLastKnownUsername()));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(
                Component.text(
                        "Category: " + (ticket.category() == null ? "-" : ticket.category()),
                        NamedTextColor.GRAY));
        lore.add(Component.text("Status: " + ticket.status().name(), NamedTextColor.GRAY));
        if (ticket.claimedByUsername() != null) {
            lore.add(
                    Component.text(
                            "Claimed by: " + ticket.claimedByUsername(), NamedTextColor.GRAY));
        }
        lore.add(Component.text("Left-click to claim", NamedTextColor.GREEN));
        lore.add(Component.text("Right-click to close", NamedTextColor.RED));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ticketIdKey, PersistentDataType.LONG, ticket.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack navIcon(String name) {
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof QueueHolder queue)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            open(player, Math.max(1, queue.page() - 1));
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 53) {
            open(player, queue.page() + 1);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) {
            return;
        }
        Long id = ticketId(clicked);
        if (id == null) {
            return;
        }
        if (event.isRightClick()) {
            plugin.services()
                    .tickets
                    .close(id, player.getUniqueId(), player.getName())
                    .thenAccept(
                            result ->
                                    plugin.scheduler()
                                            .runGlobal(
                                                    () -> {
                                                        player.sendMessage(
                                                                plugin.lang()
                                                                        .render(
                                                                                player,
                                                                                result
                                                                                                == TicketService
                                                                                                        .CloseResult
                                                                                                        .SUCCESS
                                                                                        ? "ticket.close.success"
                                                                                        : "ticket.close.already_closed",
                                                                                Placeholder
                                                                                        .unparsed(
                                                                                                "id",
                                                                                                String
                                                                                                        .valueOf(
                                                                                                                id))));
                                                        open(player, queue.page());
                                                    }));
        } else {
            plugin.services()
                    .tickets
                    .claim(id, player.getUniqueId(), player.getName())
                    .thenAccept(
                            result ->
                                    plugin.scheduler().runGlobal(() -> open(player, queue.page())));
        }
    }

    private Long ticketId(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta()
                .getPersistentDataContainer()
                .get(ticketIdKey, PersistentDataType.LONG);
    }

    private record QueueHolder(int page) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

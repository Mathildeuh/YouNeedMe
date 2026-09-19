package fr.mathildeuh.youneedme.modules.moderation;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

/**
 * A 45-slot {@code /invsee} view covering every real slot - main inventory, armor and offhand - not
 * just the 36 main slots a raw {@code Player#openInventory(target.getInventory())} call shows.
 * Edits made in the GUI are written back to the target's real inventory one tick after each
 * click/drag (so Bukkit has already applied the click to the GUI's own copy before we read it
 * back), and the view itself refreshes periodically so the viewer also sees the target's own
 * actions - picking items up, armor breaking, etc.
 */
public final class InvSeeGui implements Listener {

    private static final int MAIN_SLOTS = 36;
    private static final int HELMET = 36;
    private static final int CHESTPLATE = 37;
    private static final int LEGGINGS = 38;
    private static final int BOOTS = 39;
    private static final int OFFHAND = 40;
    private static final int GUI_SIZE = 45;

    private final YouNeedMe plugin;
    private final Map<UUID, UUID> viewerToTarget = new ConcurrentHashMap<>();
    private final Map<UUID, SchedulerAdapter.ScheduledTask> refreshTasks =
            new ConcurrentHashMap<>();

    public InvSeeGui(YouNeedMe plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player viewer, Player target) {
        Inventory inventory =
                Bukkit.createInventory(
                        new Holder(), GUI_SIZE, Component.text(target.getName() + "'s Inventory"));
        copyToGui(target, inventory);
        viewer.openInventory(inventory);
        viewerToTarget.put(viewer.getUniqueId(), target.getUniqueId());
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();
        refreshTasks.put(
                viewerId,
                plugin.scheduler()
                        .runGlobalTimer(
                                () -> {
                                    Player liveTarget = Bukkit.getPlayer(targetId);
                                    Player liveViewer = Bukkit.getPlayer(viewerId);
                                    if (liveTarget == null || liveViewer == null) {
                                        return;
                                    }
                                    Inventory top = liveViewer.getOpenInventory().getTopInventory();
                                    if (top.getHolder() instanceof Holder) {
                                        copyToGui(liveTarget, top);
                                    }
                                },
                                10L,
                                10L));
    }

    private void copyToGui(Player target, Inventory gui) {
        PlayerInventory inv = target.getInventory();
        for (int i = 0; i < MAIN_SLOTS; i++) {
            gui.setItem(i, inv.getItem(i));
        }
        gui.setItem(HELMET, inv.getHelmet());
        gui.setItem(CHESTPLATE, inv.getChestplate());
        gui.setItem(LEGGINGS, inv.getLeggings());
        gui.setItem(BOOTS, inv.getBoots());
        gui.setItem(OFFHAND, inv.getItemInOffHand());
        for (int i = OFFHAND + 1; i < GUI_SIZE; i++) {
            gui.setItem(i, null);
        }
    }

    private void syncToTarget(UUID viewerId, Inventory gui) {
        UUID targetId = viewerToTarget.get(viewerId);
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            return;
        }
        PlayerInventory inv = target.getInventory();
        for (int i = 0; i < MAIN_SLOTS; i++) {
            inv.setItem(i, gui.getItem(i));
        }
        inv.setHelmet(gui.getItem(HELMET));
        inv.setChestplate(gui.getItem(CHESTPLATE));
        inv.setLeggings(gui.getItem(LEGGINGS));
        inv.setBoots(gui.getItem(BOOTS));
        ItemStack offhand = gui.getItem(OFFHAND);
        inv.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)
                || !(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= OFFHAND + 1 && slot < GUI_SIZE) {
            event.setCancelled(true); // decorative filler row past the offhand slot
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        plugin.scheduler().runGlobalDelayed(() -> syncToTarget(viewerId, event.getInventory()), 1L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)
                || !(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        plugin.scheduler().runGlobalDelayed(() -> syncToTarget(viewerId, event.getInventory()), 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)
                || !(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        syncToTarget(viewer.getUniqueId(), event.getInventory());
        viewerToTarget.remove(viewer.getUniqueId());
        SchedulerAdapter.ScheduledTask task = refreshTasks.remove(viewer.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private record Holder() implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

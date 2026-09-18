package fr.mathildeuh.youneedme.modules.auctionhouse;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Click-to-buy browser for active auction listings - the GUI half of {@code /ah}. */
public final class AhGui implements Listener {

    private final YouNeedMe plugin;

    public AhGui(YouNeedMe plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        plugin.services()
                .auctionHouse
                .browse(page, 45)
                .thenAccept(
                        listings ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    Inventory inventory =
                                                            Bukkit.createInventory(
                                                                    new AhHolder(),
                                                                    54,
                                                                    Component.text(
                                                                            "Auction House"));
                                                    for (int i = 0;
                                                            i < listings.size() && i < 45;
                                                            i++) {
                                                        inventory.setItem(
                                                                i, listingIcon(listings.get(i)));
                                                    }
                                                    player.openInventory(inventory);
                                                }));
    }

    private ItemStack listingIcon(AuctionListing listing) {
        ItemStack stack = listing.item().clone();
        ItemMeta meta = stack.getItemMeta();
        meta.lore(
                java.util.List.of(
                        Component.text(
                                "Price: "
                                        + plugin.services()
                                                .economy
                                                .currency("default")
                                                .format(listing.price())),
                        Component.text("Seller: " + listing.sellerLastKnownUsername())));
        meta.getPersistentDataContainer()
                .set(listingIdKey(), PersistentDataType.LONG, listing.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private org.bukkit.NamespacedKey listingIdKey() {
        return new org.bukkit.NamespacedKey(plugin, "ah-listing-id");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AhHolder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) {
            return;
        }
        Long id =
                clicked.getItemMeta()
                        .getPersistentDataContainer()
                        .get(listingIdKey(), PersistentDataType.LONG);
        if (id == null) {
            return;
        }
        plugin.services()
                .auctionHouse
                .purchase(player.getUniqueId(), id)
                .thenAccept(
                        result -> {
                            if (result
                                    == fr.mathildeuh.youneedme.api.auctionhouse.AuctionHouseService
                                            .PurchaseResult.SUCCESS) {
                                plugin.scheduler().runGlobal(() -> open(player, 1));
                            }
                        });
    }

    private record AhHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

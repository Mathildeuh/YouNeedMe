package fr.mathildeuh.youneedme.modules.auctionhouse;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionHouseService;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import fr.mathildeuh.youneedme.gui.DialogInputPrompt;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The entire auction house experience as a GUI - browsing, selling, viewing your own listings, and
 * claiming expired ones - so {@code /ah} never needs a typed subcommand or argument.
 */
public final class AhGui implements Listener {

    private final YouNeedMe plugin;
    private final NamespacedKey listingIdKey;
    private final NamespacedKey auctionFlagKey;
    private final NamespacedKey minNextBidKey;

    public AhGui(YouNeedMe plugin) {
        this.plugin = plugin;
        this.listingIdKey = new NamespacedKey(plugin, "ah-listing-id");
        this.auctionFlagKey = new NamespacedKey(plugin, "ah-is-auction");
        this.minNextBidKey = new NamespacedKey(plugin, "ah-min-next-bid");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // --- Main menu ---------------------------------------------------------------------------

    public void openMenu(Player player) {
        Inventory inventory =
                Bukkit.createInventory(new MenuHolder(), 27, Component.text("Auction House"));
        inventory.setItem(
                10,
                icon(
                        Material.CHEST,
                        "<yellow><bold>Browse Listings",
                        List.of("Click to browse active listings")));
        inventory.setItem(
                12,
                icon(
                        Material.EMERALD,
                        "<green><bold>Sell an Item",
                        List.of("Click to list an item for sale")));
        inventory.setItem(
                14,
                icon(
                        Material.BOOK,
                        "<aqua><bold>My Listings",
                        List.of("View/cancel your active listings")));
        inventory.setItem(
                16,
                icon(
                        Material.CLOCK,
                        "<gold><bold>Expired Listings",
                        List.of("Claim back unsold/expired items")));
        player.openInventory(inventory);
    }

    // --- Browse --------------------------------------------------------------------------------

    public void openBrowse(Player player, int page) {
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
                                                                    new BrowseHolder(page),
                                                                    54,
                                                                    Component.text(
                                                                            "Auction House - Page "
                                                                                    + page));
                                                    for (int i = 0;
                                                            i < listings.size() && i < 45;
                                                            i++) {
                                                        inventory.setItem(
                                                                i,
                                                                listingIcon(listings.get(i), true));
                                                    }
                                                    inventory.setItem(
                                                            45,
                                                            icon(
                                                                    Material.ARROW,
                                                                    "<white>Previous Page",
                                                                    List.of()));
                                                    inventory.setItem(
                                                            49,
                                                            icon(
                                                                    Material.BARRIER,
                                                                    "<gray>Back to Menu",
                                                                    List.of()));
                                                    inventory.setItem(
                                                            53,
                                                            icon(
                                                                    Material.ARROW,
                                                                    "<white>Next Page",
                                                                    List.of()));
                                                    player.openInventory(inventory);
                                                }));
    }

    // --- Sell ------------------------------------------------------------------------------------

    public void openSell(Player player) {
        Inventory inventory =
                Bukkit.createInventory(
                        new SellHolder(), 27, Component.text("Sell on the Auction House"));
        inventory.setItem(
                18,
                icon(
                        Material.BARRIER,
                        "<gray>Cancel",
                        List.of("Returns the item to your inventory")));
        inventory.setItem(
                21,
                icon(
                        Material.EMERALD,
                        "<green><bold>List for Sale",
                        List.of("Place an item above, then click here", "Fixed price")));
        inventory.setItem(
                23,
                icon(
                        Material.GOLD_INGOT,
                        "<gold><bold>Start Auction",
                        List.of(
                                "Place an item above, then click here",
                                "Players bid until the listing expires")));
        player.openInventory(inventory);
    }

    // --- My listings -----------------------------------------------------------------------------

    public void openMyListings(Player player) {
        plugin.services()
                .auctionHouse
                .listingsBySeller(player.getUniqueId(), true)
                .thenAccept(
                        listings ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    Inventory inventory =
                                                            Bukkit.createInventory(
                                                                    new MyListingsHolder(),
                                                                    54,
                                                                    Component.text("My Listings"));
                                                    for (int i = 0;
                                                            i < listings.size() && i < 45;
                                                            i++) {
                                                        inventory.setItem(
                                                                i,
                                                                listingIcon(
                                                                        listings.get(i), false));
                                                    }
                                                    inventory.setItem(
                                                            49,
                                                            icon(
                                                                    Material.BARRIER,
                                                                    "<gray>Back to Menu",
                                                                    List.of()));
                                                    player.openInventory(inventory);
                                                }));
    }

    // --- Expired
    // -----------------------------------------------------------------------------------

    public void openExpired(Player player) {
        plugin.services()
                .auctionHouse
                .expiredAwaitingCollection(player.getUniqueId())
                .thenAccept(
                        listings ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    Inventory inventory =
                                                            Bukkit.createInventory(
                                                                    new ExpiredHolder(),
                                                                    54,
                                                                    Component.text(
                                                                            "Expired Listings"));
                                                    for (int i = 0;
                                                            i < listings.size() && i < 45;
                                                            i++) {
                                                        inventory.setItem(
                                                                i,
                                                                listingIcon(
                                                                        listings.get(i), false));
                                                    }
                                                    inventory.setItem(
                                                            49,
                                                            icon(
                                                                    Material.BARRIER,
                                                                    "<gray>Back to Menu",
                                                                    List.of()));
                                                    player.openInventory(inventory);
                                                }));
    }

    // --- Icons
    // -------------------------------------------------------------------------------------

    private ItemStack listingIcon(AuctionListing listing, boolean showSeller) {
        ItemStack stack = listing.item().clone();
        ItemMeta meta = stack.getItemMeta();
        var currency = plugin.services().economy.currency("default");
        var lore = new java.util.ArrayList<Component>();
        if (listing.auction()) {
            if (listing.currentBid() != null) {
                lore.add(
                        Component.text(
                                "Current bid: " + currency.format(listing.currentBid()),
                                NamedTextColor.GOLD));
                lore.add(
                        Component.text(
                                "By: " + listing.currentBidderUsername(), NamedTextColor.GRAY));
            } else {
                lore.add(
                        Component.text(
                                "Starting bid: " + currency.format(listing.price()),
                                NamedTextColor.GOLD));
                lore.add(Component.text("No bids yet", NamedTextColor.GRAY));
            }
            lore.add(Component.text("Click to place a bid", NamedTextColor.YELLOW));
        } else {
            lore.add(
                    Component.text(
                            "Price: " + currency.format(listing.price()), NamedTextColor.GREEN));
        }
        if (showSeller) {
            lore.add(
                    Component.text(
                            "Seller: " + listing.sellerLastKnownUsername(), NamedTextColor.GRAY));
        }
        lore.add(Component.text("ID: #" + listing.id(), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(listingIdKey, PersistentDataType.LONG, listing.id());
        if (listing.auction()) {
            double minNextBid =
                    listing.currentBid() != null ? listing.currentBid() + 1 : listing.price();
            pdc.set(auctionFlagKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(minNextBidKey, PersistentDataType.DOUBLE, minNextBid);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(
                lore.stream()
                        .<Component>map(line -> Component.text(line, NamedTextColor.GRAY))
                        .toList());
        item.setItemMeta(meta);
        return item;
    }

    // --- Clicks --------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuHolder) {
            onMenuClick(event, player);
        } else if (holder instanceof BrowseHolder browse) {
            onBrowseClick(event, player, browse.page());
        } else if (holder instanceof SellHolder) {
            onSellClick(event, player);
        } else if (holder instanceof MyListingsHolder) {
            onMyListingsClick(event, player);
        } else if (holder instanceof ExpiredHolder) {
            onExpiredClick(event, player);
        }
    }

    private void onMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        switch (event.getRawSlot()) {
            case 10 -> openBrowse(player, 1);
            case 12 -> openSell(player);
            case 14 -> openMyListings(player);
            case 16 -> openExpired(player);
            default -> {}
        }
    }

    private void onBrowseClick(InventoryClickEvent event, Player player, int page) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            openBrowse(player, Math.max(1, page - 1));
            return;
        }
        if (slot == 49) {
            openMenu(player);
            return;
        }
        if (slot == 53) {
            openBrowse(player, page + 1);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        Long id = listingId(clicked);
        if (id == null) {
            return;
        }
        if (isAuctionListing(clicked)) {
            promptBid(player, id, minNextBid(clicked), page);
            return;
        }
        plugin.services()
                .auctionHouse
                .purchase(player.getUniqueId(), id)
                .thenAccept(
                        result -> {
                            String key =
                                    switch (result) {
                                        case SUCCESS -> "ah.purchase.success";
                                        case INSUFFICIENT_FUNDS -> "ah.purchase.insufficient_funds";
                                        case INVENTORY_FULL -> "shop.inventory-full";
                                        case CANNOT_BUY_OWN_LISTING -> "ah.purchase.own_listing";
                                        case LISTING_EXPIRED -> "ah.purchase.expired";
                                        case LISTING_NOT_FOUND, LISTING_NOT_ACTIVE ->
                                                "ah.purchase.gone";
                                    };
                            plugin.scheduler()
                                    .runGlobal(
                                            () -> {
                                                player.sendMessage(
                                                        plugin.lang().render(player, key));
                                                if (result
                                                        == AuctionHouseService.PurchaseResult
                                                                .SUCCESS) {
                                                    openBrowse(player, page);
                                                }
                                            });
                        });
    }

    /** Tagged by {@link #listingIcon} - avoids a repository round-trip just to know the type. */
    private boolean isAuctionListing(ItemStack clicked) {
        Byte flag =
                clicked.getItemMeta()
                        .getPersistentDataContainer()
                        .get(auctionFlagKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private double minNextBid(ItemStack clicked) {
        Double value =
                clicked.getItemMeta()
                        .getPersistentDataContainer()
                        .get(minNextBidKey, PersistentDataType.DOUBLE);
        return value == null ? 1 : value;
    }

    private void promptBid(Player player, long listingId, double minimum, int page) {
        DialogInputPrompt.open(
                player,
                "Place a Bid",
                "Bid (minimum " + (long) minimum + ")",
                String.valueOf((long) minimum),
                bidText -> {
                    double amount = parsePriceOrNegative(bidText);
                    plugin.services()
                            .auctionHouse
                            .bid(player.getUniqueId(), listingId, amount)
                            .thenAccept(
                                    result -> {
                                        String key =
                                                switch (result) {
                                                    case SUCCESS -> "ah.bid.success";
                                                    case BID_TOO_LOW -> "ah.bid.too_low";
                                                    case INSUFFICIENT_FUNDS ->
                                                            "ah.purchase.insufficient_funds";
                                                    case CANNOT_BID_OWN_LISTING ->
                                                            "ah.purchase.own_listing";
                                                    case LISTING_EXPIRED -> "ah.purchase.expired";
                                                    case LISTING_NOT_FOUND,
                                                            LISTING_NOT_ACTIVE,
                                                            NOT_AN_AUCTION ->
                                                            "ah.purchase.gone";
                                                };
                                        plugin.scheduler()
                                                .runGlobal(
                                                        () -> {
                                                            player.sendMessage(
                                                                    plugin.lang()
                                                                            .render(player, key));
                                                            openBrowse(player, page);
                                                        });
                                    });
                });
    }

    private void onSellClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        if (slot != 13 && slot != 18 && slot != 21 && slot != 23) {
            // Anywhere else in the top inventory of this 27-slot GUI is unused chrome.
            if (slot >= 0 && slot < 27) {
                event.setCancelled(true);
            }
            return;
        }
        if (slot == 13) {
            return; // freely place/remove the item to sell
        }
        event.setCancelled(true);
        Inventory inventory = event.getInventory();
        if (slot == 18) {
            returnPendingItem(player, inventory);
            player.closeInventory();
            return;
        }
        ItemStack toSell = inventory.getItem(13);
        if (toSell == null || toSell.getType().isAir()) {
            player.sendMessage(
                    Component.text("Place an item in the slot first.", NamedTextColor.RED));
            return;
        }
        boolean auction = slot == 23;
        double minPrice =
                plugin.configManager().module("auctionhouse").getDouble("minimum-price", 1);
        DialogInputPrompt.open(
                player,
                auction ? "Starting Bid" : "Sale Price",
                (auction ? "Starting bid" : "Price") + " (minimum " + minPrice + ")",
                String.valueOf((int) minPrice),
                priceText -> {
                    double price = parsePriceOrNegative(priceText);
                    if (price < minPrice) {
                        plugin.scheduler()
                                .runGlobal(
                                        () -> {
                                            player.sendMessage(
                                                    Component.text(
                                                            "Invalid price - must be at least "
                                                                    + minPrice
                                                                    + ".",
                                                            NamedTextColor.RED));
                                            openSell(player);
                                        });
                        return;
                    }
                    inventory.setItem(13, null);
                    var listFuture =
                            auction
                                    ? plugin.services()
                                            .auctionHouse
                                            .listAuction(player.getUniqueId(), toSell, price)
                                    : plugin.services()
                                            .auctionHouse
                                            .list(player.getUniqueId(), toSell, price);
                    listFuture
                            .thenAccept(
                                    listing ->
                                            plugin.scheduler()
                                                    .runGlobal(
                                                            () -> {
                                                                player.sendMessage(
                                                                        Component.text(
                                                                                (auction
                                                                                                ? "Auction"
                                                                                                      + " started"
                                                                                                      + " at "
                                                                                                : "Listed"
                                                                                                      + " for ")
                                                                                        + plugin.services()
                                                                                                .economy
                                                                                                .currency(
                                                                                                        "default")
                                                                                                .format(
                                                                                                        price)
                                                                                        + ".",
                                                                                NamedTextColor
                                                                                        .GREEN));
                                                                player.closeInventory();
                                                            }))
                            .exceptionally(
                                    ex -> {
                                        plugin.scheduler()
                                                .runGlobal(
                                                        () -> {
                                                            player.getInventory().addItem(toSell);
                                                            player.sendMessage(
                                                                    Component.text(
                                                                            "Could not list the"
                                                                                + " item - it was"
                                                                                + " returned to"
                                                                                + " your"
                                                                                + " inventory.",
                                                                            NamedTextColor.RED));
                                                        });
                                        return null;
                                    });
                });
    }

    private void onMyListingsClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getRawSlot() == 49) {
            openMenu(player);
            return;
        }
        Long id = listingId(event.getCurrentItem());
        if (id == null) {
            return;
        }
        plugin.services()
                .auctionHouse
                .cancel(player.getUniqueId(), id)
                .thenAccept(
                        cancelled ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    player.sendMessage(
                                                            cancelled
                                                                    ? Component.text(
                                                                            "Listing cancelled -"
                                                                                + " item returned.",
                                                                            NamedTextColor.GREEN)
                                                                    : Component.text(
                                                                            "Could not cancel that"
                                                                                    + " listing.",
                                                                            NamedTextColor.RED));
                                                    openMyListings(player);
                                                }));
    }

    private void onExpiredClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getRawSlot() == 49) {
            openMenu(player);
            return;
        }
        Long id = listingId(event.getCurrentItem());
        if (id == null) {
            return;
        }
        plugin.services()
                .auctionHouse
                .collectExpired(player.getUniqueId(), id)
                .thenAccept(
                        collected ->
                                plugin.scheduler()
                                        .runGlobal(
                                                () -> {
                                                    player.sendMessage(
                                                            collected
                                                                    ? Component.text(
                                                                            "Item collected.",
                                                                            NamedTextColor.GREEN)
                                                                    : Component.text(
                                                                            "Could not collect - is"
                                                                                + " your inventory"
                                                                                + " full?",
                                                                            NamedTextColor.RED));
                                                    openExpired(player);
                                                }));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        returnPendingItem(player, event.getInventory());
    }

    private void returnPendingItem(Player player, Inventory inventory) {
        ItemStack item = inventory.getItem(13);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item);
            inventory.setItem(13, null);
        }
    }

    private Long listingId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(listingIdKey, PersistentDataType.LONG);
    }

    private static double parsePriceOrNegative(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private record MenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private record BrowseHolder(int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private record SellHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private record MyListingsHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }

    private record ExpiredHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder only");
        }
    }
}

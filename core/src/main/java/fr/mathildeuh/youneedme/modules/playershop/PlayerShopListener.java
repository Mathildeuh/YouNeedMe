package fr.mathildeuh.youneedme.modules.playershop;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.playershop.PlayerShop;
import fr.mathildeuh.youneedme.api.playershop.PlayerShopService.TradeResult;
import fr.mathildeuh.youneedme.gui.DialogInputPrompt;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Sign+chest player shop creation, trading and removal. Creation is a two-step {@link
 * DialogInputPrompt} wizard (buy price, then sell price) rather than encoding prices into the sign
 * text a player types - the sign itself is only ever plugin-rendered output once a shop exists.
 */
public final class PlayerShopListener implements Listener {

    private final YouNeedMe plugin;

    public PlayerShopListener(YouNeedMe plugin) {
        this.plugin = plugin;
    }

    private PlayerShopServiceImpl service() {
        return plugin.services().playerShops;
    }

    private String triggerLine() {
        return plugin.configManager().module("playershop").getString("trigger-line", "[Shop]");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Component firstLineComponent = event.line(0);
        String firstLine =
                firstLineComponent == null
                        ? ""
                        : PlainTextComponentSerializer.plainText().serialize(firstLineComponent);
        if (!triggerLine().equalsIgnoreCase(firstLine.trim())) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.configManager().module("playershop").getBoolean("enabled", true)) {
            event.setCancelled(true);
            return;
        }
        if (!player.hasPermission("youneedme.playershop.create")) {
            event.setCancelled(true);
            player.sendMessage(plugin.lang().render(player, "playershop.create.no_permission"));
            return;
        }
        Block chestBlock = attachedChest(event.getBlock());
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest)) {
            event.setCancelled(true);
            player.sendMessage(plugin.lang().render(player, "playershop.create.not_a_chest"));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            event.setCancelled(true);
            player.sendMessage(plugin.lang().render(player, "playershop.create.empty_hand"));
            return;
        }
        int maxShops =
                plugin.configManager().module("playershop").getInt("max-shops-per-player", 0);
        if (maxShops > 0 && service().shopsByOwner(player.getUniqueId()).size() >= maxShops) {
            event.setCancelled(true);
            player.sendMessage(plugin.lang().render(player, "playershop.create.limit_reached"));
            return;
        }

        ItemStack item = hand.clone();
        item.setAmount(1);
        Position signPosition = Position.of(event.getBlock().getLocation());
        Position chestPosition = Position.of(chestBlock.getLocation());

        event.line(0, Component.text(triggerLine()));
        event.line(1, Component.text(player.getName()));
        event.line(2, Component.text("..."));
        event.line(3, Component.text(""));

        promptBuyPrice(player, signPosition, chestPosition, item);
    }

    private void promptBuyPrice(
            Player player, Position signPosition, Position chestPosition, ItemStack item) {
        DialogInputPrompt.open(
                player,
                "Shop - buy price",
                "Price visitors pay to buy one (empty = disabled)",
                "",
                buyText -> {
                    Double buyPrice = parsePrice(buyText);
                    if (invalidNonBlank(buyText, buyPrice)) {
                        cancelCreation(player, signPosition, "playershop.create.invalid_price");
                        return;
                    }
                    promptSellPrice(player, signPosition, chestPosition, item, buyPrice);
                });
    }

    private void promptSellPrice(
            Player player,
            Position signPosition,
            Position chestPosition,
            ItemStack item,
            Double buyPrice) {
        DialogInputPrompt.open(
                player,
                "Shop - sell price",
                "Price you pay visitors to sell one (empty = disabled)",
                "",
                sellText -> {
                    Double sellPrice = parsePrice(sellText);
                    if (invalidNonBlank(sellText, sellPrice)) {
                        cancelCreation(player, signPosition, "playershop.create.invalid_price");
                        return;
                    }
                    if (buyPrice == null && sellPrice == null) {
                        cancelCreation(player, signPosition, "playershop.create.no_price");
                        return;
                    }
                    service()
                            .create(
                                    player.getUniqueId(),
                                    signPosition,
                                    chestPosition,
                                    item,
                                    buyPrice,
                                    sellPrice)
                            .thenAccept(
                                    shop ->
                                            plugin.scheduler()
                                                    .runGlobal(
                                                            () -> {
                                                                updateSign(signPosition);
                                                                player.sendMessage(
                                                                        plugin.lang()
                                                                                .render(
                                                                                        player,
                                                                                        "playershop"
                                                                                            + ".create"
                                                                                            + ".success"));
                                                            }));
                });
    }

    private static boolean invalidNonBlank(String text, Double parsed) {
        return text != null && !text.isBlank() && parsed == null;
    }

    private static Double parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void cancelCreation(Player player, Position signPosition, String messageKey) {
        player.sendMessage(plugin.lang().render(player, messageKey));
        Location location = signPosition.toLocation();
        if (location != null) {
            plugin.scheduler()
                    .runAtLocation(location, () -> location.getBlock().setType(Material.AIR));
        }
    }

    private void updateSign(Position signPosition) {
        Location location = signPosition.toLocation();
        if (location == null) {
            return;
        }
        PlayerShop shop = service().atSign(signPosition).orElse(null);
        if (shop == null || !(location.getBlock().getState() instanceof Sign sign)) {
            return;
        }
        var side = sign.getSide(Side.FRONT);
        side.line(0, Component.text(triggerLine()));
        side.line(1, Component.text(shop.ownerLastKnownUsername()));
        side.line(2, Component.text(priceLine(shop)));
        side.line(3, Component.text(itemLabel(shop.item())));
        sign.update();
    }

    private static String priceLine(PlayerShop shop) {
        StringBuilder line = new StringBuilder();
        if (shop.isBuyable()) {
            line.append("B:").append(formatPrice(shop.buyPrice()));
        }
        if (shop.isSellable()) {
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append("S:").append(formatPrice(shop.sellPrice()));
        }
        return line.toString();
    }

    private static String formatPrice(double price) {
        return price == Math.rint(price) ? String.valueOf((long) price) : String.valueOf(price);
    }

    private static String itemLabel(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private static Block attachedChest(Block signBlock) {
        if (!(signBlock.getBlockData() instanceof WallSign wallSign)) {
            return null;
        }
        BlockFace attachedTo = wallSign.getFacing().getOppositeFace();
        return signBlock.getRelative(attachedTo);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) {
            return;
        }
        Position signPosition = Position.of(block.getLocation());
        var shopOpt = service().atSign(signPosition);
        if (shopOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        PlayerShop shop = shopOpt.get();
        Player player = event.getPlayer();
        if (!player.hasPermission("youneedme.playershop.use")) {
            player.sendMessage(plugin.lang().render(player, "playershop.trade.no_permission"));
            return;
        }
        int amount = player.isSneaking() ? shop.item().getMaxStackSize() : 1;
        boolean buying = event.getAction() == Action.LEFT_CLICK_BLOCK;
        var future =
                buying
                        ? service().buy(player.getUniqueId(), shop.id(), amount)
                        : service().sell(player.getUniqueId(), shop.id(), amount);
        future.thenAccept(
                result ->
                        plugin.scheduler()
                                .runGlobal(
                                        () ->
                                                handleTradeResult(
                                                        player, shop, buying, amount, result)));
    }

    private void handleTradeResult(
            Player player, PlayerShop shop, boolean buying, int amount, TradeResult result) {
        if (result == TradeResult.SUCCESS) {
            double total = (buying ? shop.buyPrice() : shop.sellPrice()) * amount;
            player.sendMessage(
                    plugin.lang()
                            .render(
                                    player,
                                    buying
                                            ? "playershop.trade.buy_success"
                                            : "playershop.trade.sell_success",
                                    Placeholder.unparsed("amount", String.valueOf(amount)),
                                    Placeholder.unparsed("item", itemLabel(shop.item())),
                                    Placeholder.unparsed("price", formatPrice(total))));
            return;
        }
        String key =
                switch (result) {
                    case NOT_BUYABLE -> "playershop.trade.not_buyable";
                    case NOT_SELLABLE -> "playershop.trade.not_sellable";
                    case CANNOT_TRADE_OWN_SHOP -> "playershop.trade.own_shop";
                    case CHEST_MISSING -> "playershop.trade.chest_missing";
                    case OUT_OF_STOCK -> "playershop.trade.out_of_stock";
                    case CHEST_FULL -> "playershop.trade.chest_full";
                    case INVENTORY_FULL -> "playershop.trade.inventory_full";
                    case INSUFFICIENT_FUNDS -> "playershop.trade.insufficient_funds";
                    case SHOP_NOT_FOUND -> "playershop.trade.shop_not_found";
                    case SUCCESS -> throw new IllegalStateException("handled above");
                };
        player.sendMessage(plugin.lang().render(player, key));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Position position = Position.of(block.getLocation());
        var shopOpt = service().atSign(position).or(() -> service().atChest(position));
        if (shopOpt.isEmpty()) {
            return;
        }
        PlayerShop shop = shopOpt.get();
        Player player = event.getPlayer();
        boolean owner = shop.owner().equals(player.getUniqueId());
        if (!owner && !player.hasPermission("youneedme.playershop.remove.others")) {
            event.setCancelled(true);
            player.sendMessage(plugin.lang().render(player, "playershop.remove.no_permission"));
            return;
        }
        service().remove(shop.id());
        player.sendMessage(plugin.lang().render(player, "playershop.remove.success"));
    }
}

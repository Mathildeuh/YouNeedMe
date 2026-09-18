package fr.mathildeuh.youneedme.modules.auctionhouse;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionListing;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AhCommand extends YnmCommand {

    private final AhGui gui;

    public AhCommand(YouNeedMe plugin, AhGui gui) {
        super(plugin, "youneedme.ah.use", true);
        this.gui = gui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "page";
        switch (sub) {
            case "sell" -> sell(sender, player, args);
            case "cancel" -> cancel(sender, player, args);
            case "expired" -> expired(sender, player);
            case "listings" -> listings(sender, player);
            case "notifications" -> send(sender, "ah.no_auctions");
            case "page" -> gui.open(player, args.length > 1 ? parsePageNumber(args[1]) : 1);
            default -> gui.open(player, parsePageNumber(sub));
        }
    }

    private static int parsePageNumber(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sell(CommandSender sender, Player player, String[] args) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "ah.no_item");
            return;
        }
        if (args.length < 2) {
            send(sender, "command.usage.ah");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            send(sender, "ah.invalid_price");
            return;
        }
        double minPrice =
                plugin.configManager().module("auctionhouse").getDouble("minimum-price", 1);
        if (price < minPrice) {
            send(sender, "ah.price_too_low", Placeholder.unparsed("min", String.valueOf(minPrice)));
            return;
        }
        ItemStack listed = hand.clone();
        services()
                .auctionHouse
                .list(player.getUniqueId(), listed, price)
                .thenAccept(
                        listing -> {
                            player.getInventory().setItemInMainHand(null);
                            long hours =
                                    plugin.configManager()
                                            .module("auctionhouse")
                                            .getLong("listing-duration-hours", 72);
                            send(
                                    sender,
                                    "ah.listed",
                                    Placeholder.unparsed(
                                            "price",
                                            services().economy.currency("default").format(price)),
                                    Placeholder.unparsed("duration", String.valueOf(hours)));
                        });
    }

    private void cancel(CommandSender sender, Player player, String[] args) {
        if (args.length < 2) {
            send(sender, "command.usage.ah");
            return;
        }
        long id = parseId(args[1]);
        services()
                .auctionHouse
                .cancel(player.getUniqueId(), id)
                .thenAccept(
                        cancelled -> send(sender, cancelled ? "ah.cancelled" : "ah.cancel_failed"));
    }

    private void expired(CommandSender sender, Player player) {
        services()
                .auctionHouse
                .expiredAwaitingCollection(player.getUniqueId())
                .thenAccept(
                        listings -> {
                            if (listings.isEmpty()) {
                                send(sender, "ah.no_expired");
                                return;
                            }
                            for (AuctionListing listing : listings) {
                                services()
                                        .auctionHouse
                                        .collectExpired(player.getUniqueId(), listing.id());
                            }
                            send(sender, "ah.claimed_expired");
                        });
    }

    private void listings(CommandSender sender, Player player) {
        services()
                .auctionHouse
                .listingsBySeller(player.getUniqueId(), true)
                .thenAccept(
                        listings -> {
                            if (listings.isEmpty()) {
                                send(sender, "ah.no_listings");
                                return;
                            }
                            send(sender, "ah.your_listings_header");
                            for (AuctionListing listing : listings) {
                                send(
                                        sender,
                                        "ah.listing_entry",
                                        Placeholder.unparsed("id", String.valueOf(listing.id())),
                                        Placeholder.unparsed(
                                                "item", listing.item().getType().name()),
                                        Placeholder.unparsed(
                                                "price",
                                                services()
                                                        .economy
                                                        .currency("default")
                                                        .format(listing.price())),
                                        Placeholder.unparsed(
                                                "time",
                                                TimeParser.format(
                                                        listing.expiresAt()
                                                                - System.currentTimeMillis())));
                            }
                        });
    }

    private static long parseId(String raw) {
        try {
            return Long.parseLong(raw.replace("#", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? List.of("sell", "cancel", "expired", "listings", "notifications", "page")
                : List.of();
    }
}

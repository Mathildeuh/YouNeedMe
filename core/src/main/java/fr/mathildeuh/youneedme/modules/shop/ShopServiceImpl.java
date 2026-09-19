package fr.mathildeuh.youneedme.modules.shop;

import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.shop.ShopCategory;
import fr.mathildeuh.youneedme.api.shop.ShopItem;
import fr.mathildeuh.youneedme.api.shop.ShopService;
import fr.mathildeuh.youneedme.api.storage.ShopRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ShopServiceImpl implements ShopService {

    private final ShopRepository repository;
    private final EconomyService economy;
    private volatile List<ShopCategory> categories;
    private final Supplier<List<ShopCategory>> reloader;
    private final boolean enabled;

    public ShopServiceImpl(
            ShopRepository repository,
            EconomyService economy,
            List<ShopCategory> initial,
            Supplier<List<ShopCategory>> reloader,
            boolean enabled) {
        this.repository = repository;
        this.economy = economy;
        this.categories = initial;
        this.reloader = reloader;
        this.enabled = enabled;
    }

    @Override
    public List<ShopCategory> categories() {
        return categories;
    }

    @Override
    public Optional<ShopCategory> category(String id) {
        return categories.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @Override
    public Optional<ShopItem> item(String itemId) {
        return categories.stream()
                .flatMap(c -> c.items().stream())
                .filter(i -> i.id().equals(itemId))
                .findFirst();
    }

    @Override
    public CompletableFuture<TradeResult> buy(UUID player, String itemId, int amount) {
        if (!enabled) {
            return CompletableFuture.completedFuture(TradeResult.SHOP_DISABLED);
        }
        Optional<Pair> pair = findWithCategory(itemId);
        if (pair.isEmpty()) {
            return CompletableFuture.completedFuture(TradeResult.ITEM_NOT_FOUND);
        }
        ShopItem shopItem = pair.get().item;
        if (!shopItem.isBuyable()) {
            return CompletableFuture.completedFuture(TradeResult.NOT_BUYABLE);
        }
        Double buyPrice = shopItem.buyPrice();
        if (buyPrice == null) {
            return CompletableFuture.completedFuture(TradeResult.NOT_BUYABLE);
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null && countFreeSlots(online) < 1) {
            return CompletableFuture.completedFuture(TradeResult.INVENTORY_FULL);
        }
        double total = buyPrice * amount;
        CompletableFuture<Optional<Integer>> stockCheck =
                shopItem.stock() == null
                        ? CompletableFuture.completedFuture(Optional.empty())
                        : repository
                                .getStock(pair.get().categoryId, itemId)
                                .thenApply(s -> s.or(() -> Optional.of(shopItem.stock())));
        return stockCheck.thenCompose(
                stockOpt -> {
                    if (stockOpt.isPresent() && stockOpt.get() < amount) {
                        return CompletableFuture.completedFuture(
                                stockOpt.get() <= 0
                                        ? TradeResult.OUT_OF_STOCK
                                        : TradeResult.INSUFFICIENT_STOCK_REQUESTED);
                    }
                    return economy.withdraw(player, total)
                            .thenCompose(
                                    result -> {
                                        if (!result.isSuccess()) {
                                            return CompletableFuture.completedFuture(
                                                    TradeResult.INSUFFICIENT_FUNDS);
                                        }
                                        CompletableFuture<Void> stockUpdate =
                                                shopItem.stock() == null
                                                        ? CompletableFuture.completedFuture(null)
                                                        : repository
                                                                .adjustStock(
                                                                        pair.get().categoryId,
                                                                        itemId,
                                                                        -amount)
                                                                .thenAccept(v -> {});
                                        return stockUpdate.thenApply(
                                                v -> {
                                                    if (online != null) {
                                                        ItemStack stack =
                                                                shopItem.display().clone();
                                                        stack.setAmount(amount);
                                                        online.getInventory().addItem(stack);
                                                    }
                                                    return TradeResult.SUCCESS;
                                                });
                                    });
                });
    }

    @Override
    public CompletableFuture<TradeResult> sell(UUID player, String itemId, int amount) {
        if (!enabled) {
            return CompletableFuture.completedFuture(TradeResult.SHOP_DISABLED);
        }
        Optional<Pair> pair = findWithCategory(itemId);
        if (pair.isEmpty()) {
            return CompletableFuture.completedFuture(TradeResult.ITEM_NOT_FOUND);
        }
        ShopItem shopItem = pair.get().item;
        if (!shopItem.isSellable()) {
            return CompletableFuture.completedFuture(TradeResult.NOT_SELLABLE);
        }
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return CompletableFuture.completedFuture(TradeResult.INSUFFICIENT_ITEMS);
        }
        if (!removeItems(online, shopItem.display(), amount)) {
            return CompletableFuture.completedFuture(TradeResult.INSUFFICIENT_ITEMS);
        }
        Double sellPrice = shopItem.sellPrice();
        if (sellPrice == null) {
            return CompletableFuture.completedFuture(TradeResult.NOT_SELLABLE);
        }
        double total = sellPrice * amount;
        return economy.deposit(player, total)
                .thenCompose(
                        result -> {
                            CompletableFuture<Void> stockUpdate =
                                    shopItem.stock() == null
                                            ? CompletableFuture.completedFuture(null)
                                            : repository
                                                    .adjustStock(
                                                            pair.get().categoryId, itemId, amount)
                                                    .thenAccept(v -> {});
                            return stockUpdate.thenApply(v -> TradeResult.SUCCESS);
                        });
    }

    @Override
    public CompletableFuture<QuickSellResult> quickSell(UUID player, boolean wholeInventory) {
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return CompletableFuture.completedFuture(new QuickSellResult(0, 0));
        }
        ItemStack[] contents =
                wholeInventory
                        ? online.getInventory().getStorageContents()
                        : new ItemStack[] {online.getInventory().getItemInMainHand()};
        int totalSold = 0;
        double totalWorth = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            double worth = worth(stack);
            if (worth <= 0) {
                continue;
            }
            totalSold += stack.getAmount();
            totalWorth += worth * stack.getAmount();
            if (!wholeInventory) {
                online.getInventory().setItemInMainHand(null);
            } else {
                // getStorageContents() returns a fresh copy each call - mutating that copy (as
                // this used to) never touched the real inventory. contents IS the array written
                // back below, so it must be nulled here instead.
                contents[i] = null;
            }
        }
        if (wholeInventory) {
            online.getInventory().setStorageContents(contents);
        }
        if (totalSold == 0) {
            return CompletableFuture.completedFuture(new QuickSellResult(0, 0));
        }
        double finalWorth = totalWorth;
        int finalSold = totalSold;
        return economy.deposit(player, totalWorth)
                .thenApply(r -> new QuickSellResult(finalSold, finalWorth));
    }

    @Override
    public double worth(ItemStack stack) {
        return item(materialKey(stack))
                .map(i -> i.sellPrice() == null ? 0 : i.sellPrice())
                .orElse(0.0);
    }

    @Override
    public CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(() -> this.categories = reloader.get());
    }

    private Optional<Pair> findWithCategory(String itemId) {
        for (ShopCategory category : categories) {
            for (ShopItem item : category.items()) {
                if (item.id().equals(itemId)) {
                    return Optional.of(new Pair(category.id(), item));
                }
            }
        }
        return Optional.empty();
    }

    private String materialKey(ItemStack stack) {
        for (ShopCategory category : categories) {
            for (ShopItem item : category.items()) {
                if (item.display().getType() == stack.getType()) {
                    return item.id();
                }
            }
        }
        return "";
    }

    private static int countFreeSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    private static boolean removeItems(Player player, ItemStack template, int amount) {
        int available = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(template)) {
                available += stack.getAmount();
            }
        }
        if (available < amount) {
            return false;
        }
        ItemStack toRemove = template.clone();
        toRemove.setAmount(amount);
        player.getInventory().removeItem(toRemove);
        return true;
    }

    private record Pair(String categoryId, ShopItem item) {}
}

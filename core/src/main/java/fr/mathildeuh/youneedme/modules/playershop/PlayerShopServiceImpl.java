package fr.mathildeuh.youneedme.modules.playershop;

import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.playershop.PlayerShop;
import fr.mathildeuh.youneedme.api.playershop.PlayerShopService;
import fr.mathildeuh.youneedme.api.storage.PlayerShopRepository;
import fr.mathildeuh.youneedme.util.KeyedMutex;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Chest+sign player shops. Every lookup ({@link #atSign}/{@link #atChest}) is backed by an
 * in-memory cache warmed once at startup via {@link #load()} - a sign/chest interaction listener
 * cannot reasonably wait on an async storage round-trip - and kept in sync with storage on every
 * create/remove. Trades are serialized per shop through {@link KeyedMutex} so two players clicking
 * the same shop at once can't race into overselling the chest's stock.
 */
public final class PlayerShopServiceImpl implements PlayerShopService {

    private final PlayerShopRepository repository;
    private final EconomyService economy;
    private final Logger logger;
    private final KeyedMutex<Long> mutex = new KeyedMutex<>();
    private final Map<Long, PlayerShop> byId = new ConcurrentHashMap<>();
    private final Map<Position, PlayerShop> bySign = new ConcurrentHashMap<>();
    private final Map<Position, PlayerShop> byChest = new ConcurrentHashMap<>();

    public PlayerShopServiceImpl(
            PlayerShopRepository repository, EconomyService economy, Logger logger) {
        this.repository = repository;
        this.economy = economy;
        this.logger = logger;
    }

    public void load() {
        repository
                .findAllShops()
                .thenAccept(shops -> shops.forEach(this::cache))
                .exceptionally(
                        ex -> {
                            logger.log(Level.SEVERE, "Could not load player shops", ex);
                            return null;
                        });
    }

    private void cache(PlayerShop shop) {
        byId.put(shop.id(), shop);
        bySign.put(shop.signLocation(), shop);
        byChest.put(shop.chestLocation(), shop);
    }

    private void uncache(PlayerShop shop) {
        byId.remove(shop.id());
        bySign.remove(shop.signLocation());
        byChest.remove(shop.chestLocation());
    }

    @Override
    public Optional<PlayerShop> atSign(Position signLocation) {
        return Optional.ofNullable(bySign.get(signLocation));
    }

    @Override
    public Optional<PlayerShop> atChest(Position chestLocation) {
        return Optional.ofNullable(byChest.get(chestLocation));
    }

    @Override
    public List<PlayerShop> shopsByOwner(UUID owner) {
        return byId.values().stream().filter(s -> s.owner().equals(owner)).toList();
    }

    @Override
    public CompletableFuture<PlayerShop> create(
            UUID owner,
            Position signLocation,
            Position chestLocation,
            ItemStack item,
            Double buyPrice,
            Double sellPrice) {
        Player player = Bukkit.getPlayer(owner);
        String username = player != null ? player.getName() : owner.toString();
        PlayerShop shop =
                new PlayerShop(
                        0,
                        owner,
                        username,
                        signLocation,
                        chestLocation,
                        item.clone(),
                        buyPrice,
                        sellPrice);
        return repository
                .save(shop)
                .thenApply(
                        saved -> {
                            cache(saved);
                            return saved;
                        });
    }

    @Override
    public CompletableFuture<Void> remove(long shopId) {
        PlayerShop shop = byId.get(shopId);
        if (shop == null) {
            return CompletableFuture.completedFuture(null);
        }
        uncache(shop);
        return repository.delete(shopId);
    }

    @Override
    public CompletableFuture<TradeResult> buy(UUID buyer, long shopId, int amount) {
        return mutex.runExclusive(shopId, () -> doBuy(buyer, shopId, amount));
    }

    private CompletableFuture<TradeResult> doBuy(UUID buyer, long shopId, int amount) {
        PlayerShop shop = byId.get(shopId);
        if (shop == null) {
            return CompletableFuture.completedFuture(TradeResult.SHOP_NOT_FOUND);
        }
        if (!shop.isBuyable()) {
            return CompletableFuture.completedFuture(TradeResult.NOT_BUYABLE);
        }
        if (shop.owner().equals(buyer)) {
            return CompletableFuture.completedFuture(TradeResult.CANNOT_TRADE_OWN_SHOP);
        }
        Player player = Bukkit.getPlayer(buyer);
        if (player == null) {
            return CompletableFuture.completedFuture(TradeResult.SHOP_NOT_FOUND);
        }
        Inventory chestInventory = chestInventory(shop);
        if (chestInventory == null) {
            return CompletableFuture.completedFuture(TradeResult.CHEST_MISSING);
        }
        if (countMatching(chestInventory, shop.item()) < amount) {
            return CompletableFuture.completedFuture(TradeResult.OUT_OF_STOCK);
        }
        if (countFreeSlots(player.getInventory()) < 1) {
            return CompletableFuture.completedFuture(TradeResult.INVENTORY_FULL);
        }
        double total = shop.buyPrice() * amount;
        return economy.withdraw(buyer, total)
                .thenCompose(
                        withdrawResult -> {
                            if (!withdrawResult.isSuccess()) {
                                return CompletableFuture.completedFuture(
                                        TradeResult.INSUFFICIENT_FUNDS);
                            }
                            return economy.deposit(shop.owner(), total)
                                    .thenApply(
                                            depositResult -> {
                                                removeMatching(chestInventory, shop.item(), amount);
                                                giveItems(player, shop.item(), amount);
                                                return TradeResult.SUCCESS;
                                            });
                        });
    }

    @Override
    public CompletableFuture<TradeResult> sell(UUID seller, long shopId, int amount) {
        return mutex.runExclusive(shopId, () -> doSell(seller, shopId, amount));
    }

    private CompletableFuture<TradeResult> doSell(UUID seller, long shopId, int amount) {
        PlayerShop shop = byId.get(shopId);
        if (shop == null) {
            return CompletableFuture.completedFuture(TradeResult.SHOP_NOT_FOUND);
        }
        if (!shop.isSellable()) {
            return CompletableFuture.completedFuture(TradeResult.NOT_SELLABLE);
        }
        if (shop.owner().equals(seller)) {
            return CompletableFuture.completedFuture(TradeResult.CANNOT_TRADE_OWN_SHOP);
        }
        Player player = Bukkit.getPlayer(seller);
        if (player == null) {
            return CompletableFuture.completedFuture(TradeResult.SHOP_NOT_FOUND);
        }
        if (countMatching(player.getInventory(), shop.item()) < amount) {
            return CompletableFuture.completedFuture(TradeResult.OUT_OF_STOCK);
        }
        Inventory chestInventory = chestInventory(shop);
        if (chestInventory == null) {
            return CompletableFuture.completedFuture(TradeResult.CHEST_MISSING);
        }
        if (countFreeSlots(chestInventory) < 1) {
            return CompletableFuture.completedFuture(TradeResult.CHEST_FULL);
        }
        double total = shop.sellPrice() * amount;
        return economy.withdraw(shop.owner(), total)
                .thenCompose(
                        withdrawResult -> {
                            if (!withdrawResult.isSuccess()) {
                                return CompletableFuture.completedFuture(
                                        TradeResult.INSUFFICIENT_FUNDS);
                            }
                            return economy.deposit(seller, total)
                                    .thenApply(
                                            depositResult -> {
                                                removeMatching(
                                                        player.getInventory(), shop.item(), amount);
                                                ItemStack toAdd = shop.item().clone();
                                                toAdd.setAmount(amount);
                                                chestInventory.addItem(toAdd);
                                                return TradeResult.SUCCESS;
                                            });
                        });
    }

    private static Inventory chestInventory(PlayerShop shop) {
        Location location = shop.chestLocation().toLocation();
        if (location == null) {
            return null;
        }
        Block block = location.getBlock();
        if (!(block.getState() instanceof Chest chest)) {
            return null;
        }
        return chest.getInventory();
    }

    private static int countMatching(Inventory inventory, ItemStack template) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static int countFreeSlots(Inventory inventory) {
        int free = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    private static void removeMatching(Inventory inventory, ItemStack template, int amount) {
        ItemStack toRemove = template.clone();
        toRemove.setAmount(amount);
        inventory.removeItem(toRemove);
    }

    private static void giveItems(Player player, ItemStack template, int amount) {
        ItemStack toGive = template.clone();
        toGive.setAmount(amount);
        player.getInventory().addItem(toGive);
    }
}

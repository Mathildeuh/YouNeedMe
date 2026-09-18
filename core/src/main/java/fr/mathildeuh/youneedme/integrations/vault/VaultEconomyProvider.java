package fr.mathildeuh.youneedme.integrations.vault;

import fr.mathildeuh.youneedme.api.economy.EconomyResult;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import java.util.List;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Exposes YouNeedMe's {@link EconomyService} as a Vault {@link Economy} provider, so any other
 * plugin can transact against YouNeedMe's balances without knowing YouNeedMe exists.
 *
 * <p>Vault's {@code Economy} contract is entirely synchronous, while {@link EconomyService} is
 * async end to end (every call may hit the configured storage backend) - every method here blocks
 * the calling thread on the underlying {@link java.util.concurrent.CompletableFuture} via {@code
 * join()}. That is the accepted cost of Vault compatibility for any database-backed economy: Vault
 * calls are comparatively rare (a shop/quest plugin checking or moving a balance), not a per-tick
 * hot path.
 *
 * <p>YouNeedMe has no concept of per-world balances or player-owned banks, so the world-scoped and
 * bank-related methods degrade to their single-currency, unsupported equivalents rather than
 * throwing.
 */
public final class VaultEconomyProvider implements Economy {

    private final EconomyService economy;

    public VaultEconomyProvider(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "YouNeedMe";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return economy.currency(EconomyService.DEFAULT_CURRENCY).decimalPlaces();
    }

    @Override
    public String format(double amount) {
        return economy.currency(EconomyService.DEFAULT_CURRENCY).format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return economy.currency(EconomyService.DEFAULT_CURRENCY).pluralName();
    }

    @Override
    public String currencyNameSingular() {
        return economy.currency(EconomyService.DEFAULT_CURRENCY).singularName();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        // Accounts are implicit: any UUID has a (possibly default) balance the moment it's read.
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.balance(player.getUniqueId()).join();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player.getUniqueId(), EconomyService.DEFAULT_CURRENCY, amount).join();
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return toResponse(economy.withdraw(player.getUniqueId(), amount).join());
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return toResponse(economy.deposit(player.getUniqueId(), amount).join());
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        economy.balance(player.getUniqueId()).join();
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private EconomyResponse toResponse(EconomyResult result) {
        return new EconomyResponse(
                0,
                result.balanceAfter(),
                result.isSuccess() ? ResponseType.SUCCESS : ResponseType.FAILURE,
                result.isSuccess() ? "" : String.valueOf(result.message()));
    }

    // --- Bank accounts: unsupported, YouNeedMe has no concept of shared player-owned banks. ---

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(
                0, 0, ResponseType.NOT_IMPLEMENTED, "YouNeedMe has no bank support");
    }
}

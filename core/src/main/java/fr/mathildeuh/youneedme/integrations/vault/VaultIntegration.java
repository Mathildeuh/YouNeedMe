package fr.mathildeuh.youneedme.integrations.vault;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

/**
 * Registers YouNeedMe's economy as a Vault {@link Economy} provider, soft-depend, if Vault is
 * installed.
 */
public final class VaultIntegration {

    private VaultIntegration() {}

    public static void enable(YouNeedMe plugin, EconomyService economy) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - skipping the Economy provider integration.");
            return;
        }
        Bukkit.getServicesManager()
                .register(
                        Economy.class,
                        new VaultEconomyProvider(economy),
                        plugin,
                        ServicePriority.Highest);
        plugin.getLogger().info("Registered YouNeedMe as the Vault Economy provider.");
    }
}

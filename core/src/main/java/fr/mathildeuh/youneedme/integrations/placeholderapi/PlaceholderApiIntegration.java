package fr.mathildeuh.youneedme.integrations.placeholderapi;

import fr.mathildeuh.youneedme.YouNeedMe;
import org.bukkit.Bukkit;

public final class PlaceholderApiIntegration {

    private PlaceholderApiIntegration() {}

    public static void enable(YouNeedMe plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new YnmPlaceholderExpansion(plugin).register();
        plugin.getLogger().info("Registered the YouNeedMe PlaceholderAPI expansion.");
    }
}

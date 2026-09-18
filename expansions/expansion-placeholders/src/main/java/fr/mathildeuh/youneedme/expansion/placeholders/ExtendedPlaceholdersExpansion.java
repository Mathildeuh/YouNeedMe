package fr.mathildeuh.youneedme.expansion.placeholders;

import fr.mathildeuh.youneedme.expansion.Expansion;
import fr.mathildeuh.youneedme.expansion.ExpansionContext;
import org.bukkit.Bukkit;

/**
 * Official example expansion: shows the full lifecycle a third-party expansion goes through ({@link
 * #onLoad}/{@link #onEnable}/{@link #onDisable}), how to reach YouNeedMe's services through {@code
 * fr.mathildeuh.youneedme.api.YouNeedMeAPI} exactly like any standalone plugin would, and how to
 * register a PlaceholderAPI expansion of its own from within that lifecycle. Intended as living
 * documentation for anyone writing a real expansion, not as a feature server owners need to
 * configure.
 */
public final class ExtendedPlaceholdersExpansion implements Expansion {

    private ExpansionContext context;
    private ExtendedPapiHook papiHook;

    @Override
    public void onLoad(ExpansionContext context) {
        this.context = context;
        context.logger().info("Extended placeholders expansion loaded.");
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            context.logger().info("PlaceholderAPI not found - skipping the extra placeholders.");
            return;
        }
        papiHook = new ExtendedPapiHook();
        papiHook.register();
        context.logger().info("Registered %youneedme_extended_*% placeholders.");
    }

    @Override
    public void onDisable() {
        if (papiHook != null) {
            papiHook.unregister();
        }
    }
}

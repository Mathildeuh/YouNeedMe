package fr.mathildeuh.youneedme.expansion;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * What an {@link Expansion} is handed on {@link Expansion#onLoad}. Use {@link #hostPlugin()} with
 * the regular Bukkit APIs to register listeners/commands ({@code
 * Bukkit.getPluginManager().registerEvents(listener, context.hostPlugin())}); use {@code
 * fr.mathildeuh.youneedme.api.YouNeedMeAPI} (from the {@code api} module) to reach YouNeedMe's
 * services - both work exactly as they would from a standalone plugin.
 */
public interface ExpansionContext {

    ExpansionDescription description();

    /** Private data folder for this expansion: {@code plugins/YouNeedMe/expansions/data/<id>/}. */
    File dataFolder();

    Logger logger();

    /** The YouNeedMe plugin instance, for APIs (event/command registration, schedulers) that need a {@link Plugin}. */
    Plugin hostPlugin();
}

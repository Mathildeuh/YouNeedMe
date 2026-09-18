package fr.mathildeuh.youneedme.integrations.metrics;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.scheduler.ServerEnvironment;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * bStats usage metrics - anonymous, aggregate-only, and opt-out entirely through bStats' own
 * generated {@code plugins/bStats/config.yml} (no separate toggle needed here, matching every other
 * plugin using bStats). Never sends anything player-identifiable.
 */
public final class PluginMetrics {

    private static final int BSTATS_PLUGIN_ID = 34116;

    private PluginMetrics() {}

    public static void enable(YouNeedMe plugin) {
        Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(
                new SimplePie("storage_backend", () -> plugin.services().storage.type().name()));
        metrics.addCustomChart(
                new SimplePie(
                        "server_software",
                        () -> ServerEnvironment.isFolia() ? "Folia" : "Paper/Spigot"));
        metrics.addCustomChart(
                new SimplePie("default_language", () -> plugin.lang().defaultLocale()));
    }
}

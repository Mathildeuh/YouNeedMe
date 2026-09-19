package fr.mathildeuh.youneedme.modules.scoreboard;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.scoreboard.ScoreboardService;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import fr.mathildeuh.youneedme.scheduler.ServerEnvironment;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

public final class ScoreboardModule {

    private ScoreboardModule() {}

    public static ScoreboardServiceImpl enable(YouNeedMe plugin) {
        boolean tabPresent = Bukkit.getPluginManager().getPlugin("TAB") != null;
        boolean configuredEnabled =
                plugin.configManager().module("scoreboard").getBoolean("enabled", true);
        boolean folia = ServerEnvironment.isFolia();
        boolean active = shouldStart(folia, tabPresent, configuredEnabled);
        if (folia && configuredEnabled && !tabPresent) {
            plugin.getLogger()
                    .warning(
                            "Built-in scoreboard disabled: Folia does not support Bukkit's"
                                    + " scoreboard API yet.");
        }
        ScoreboardServiceImpl service = new ScoreboardServiceImpl(active);
        plugin.getServer()
                .getServicesManager()
                .register(ScoreboardService.class, service, plugin, ServicePriority.Normal);

        CommandRegistrar.register(plugin, "scoreboard", new ScoreboardCommand(plugin));
        if (active) {
            new ScoreboardRenderer(plugin, service).start();
        }
        return service;
    }

    static boolean shouldStart(boolean folia, boolean tabPresent, boolean configuredEnabled) {
        return !folia && !tabPresent && configuredEnabled;
    }
}

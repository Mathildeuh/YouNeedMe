package fr.mathildeuh.youneedme.modules.scoreboard;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.scoreboard.ScoreboardService;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

public final class ScoreboardModule {

    private ScoreboardModule() {}

    public static ScoreboardServiceImpl enable(YouNeedMe plugin) {
        boolean tabPresent = Bukkit.getPluginManager().getPlugin("TAB") != null;
        boolean active =
                !tabPresent
                        && plugin.configManager().module("scoreboard").getBoolean("enabled", true);
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
}

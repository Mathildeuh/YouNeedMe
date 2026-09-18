package fr.mathildeuh.youneedme.modules.homes;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.homes.HomeService;
import fr.mathildeuh.youneedme.api.storage.HomeRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

public final class HomesModule {

    private HomesModule() {}

    public static HomeServiceImpl enable(YouNeedMe plugin, HomeRepository repository) {
        int defaultLimit = plugin.configManager().module("homes").getInt("default-limit", 3);
        HomeServiceImpl service = new HomeServiceImpl(repository, plugin.scheduler(), defaultLimit);
        plugin.getServer()
                .getServicesManager()
                .register(HomeService.class, service, plugin, ServicePriority.Normal);

        CommandRegistrar.register(plugin, "sethome", new SetHomeCommand(plugin));
        CommandRegistrar.register(plugin, "delhome", new DelHomeCommand(plugin));
        CommandRegistrar.register(plugin, "home", new HomeCommand(plugin));
        CommandRegistrar.register(plugin, "homes", new HomesCommand(plugin));
        return service;
    }
}

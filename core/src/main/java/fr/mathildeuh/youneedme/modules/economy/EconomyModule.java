package fr.mathildeuh.youneedme.modules.economy;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.storage.EconomyRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

/** Wires the economy service and its commands into the running plugin. */
public final class EconomyModule {

    private EconomyModule() {}

    public static EconomyServiceImpl enable(YouNeedMe plugin, EconomyRepository repository) {
        var config = plugin.configManager().module("economy");
        EconomyServiceImpl service = new EconomyServiceImpl(repository, plugin.scheduler(), config);
        plugin.getServer()
                .getServicesManager()
                .register(EconomyService.class, service, plugin, ServicePriority.Normal);

        CommandRegistrar.register(plugin, "balance", new BalanceCommand(plugin));
        CommandRegistrar.register(plugin, "baltop", new BalTopCommand(plugin));
        CommandRegistrar.register(plugin, "pay", new PayCommand(plugin));
        CommandRegistrar.register(plugin, "eco", new EcoCommand(plugin));
        return service;
    }
}

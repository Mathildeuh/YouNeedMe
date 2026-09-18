package fr.mathildeuh.youneedme.modules.auctionhouse;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.auctionhouse.AuctionHouseService;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.storage.AuctionRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

public final class AuctionHouseModule {

    private AuctionHouseModule() {}

    public static AuctionHouseServiceImpl enable(
            YouNeedMe plugin, AuctionRepository repository, EconomyService economy) {
        var config = plugin.configManager().module("auctionhouse");
        AuctionHouseServiceImpl service =
                new AuctionHouseServiceImpl(repository, economy, plugin.scheduler(), config);
        plugin.getServer()
                .getServicesManager()
                .register(AuctionHouseService.class, service, plugin, ServicePriority.Normal);

        AhGui gui = new AhGui(plugin);
        CommandRegistrar.register(plugin, "ah", new AhCommand(plugin, gui));

        long sweepIntervalTicks = config.getLong("expiry-sweep-interval-seconds", 300) * 20L;
        plugin.scheduler()
                .runGlobalTimer(service::expireOverdue, sweepIntervalTicks, sweepIntervalTicks);
        return service;
    }
}

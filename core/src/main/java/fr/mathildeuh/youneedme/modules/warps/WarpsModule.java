package fr.mathildeuh.youneedme.modules.warps;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.storage.WarpRepository;
import fr.mathildeuh.youneedme.api.warps.WarpService;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

public final class WarpsModule {

    private WarpsModule() {}

    public static WarpServiceImpl enable(YouNeedMe plugin, WarpRepository repository) {
        WarpServiceImpl service = new WarpServiceImpl(repository);
        service.warmCache()
                .exceptionally(
                        t -> {
                            plugin.getLogger()
                                    .warning("Failed to warm the warp cache: " + t.getMessage());
                            return null;
                        });
        plugin.getServer()
                .getServicesManager()
                .register(WarpService.class, service, plugin, ServicePriority.Normal);

        CommandRegistrar.register(plugin, "warp", new WarpCommand(plugin));
        CommandRegistrar.register(plugin, "setwarp", new SetWarpCommand(plugin));
        CommandRegistrar.register(plugin, "delwarp", new DelWarpCommand(plugin));
        CommandRegistrar.register(plugin, "warps", new WarpsCommand(plugin));
        CommandRegistrar.register(plugin, "warpadmin", new WarpAdminCommand(plugin));
        return service;
    }
}

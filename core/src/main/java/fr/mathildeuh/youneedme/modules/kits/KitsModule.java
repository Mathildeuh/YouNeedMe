package fr.mathildeuh.youneedme.modules.kits;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.kits.KitService;
import fr.mathildeuh.youneedme.api.storage.KitRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

public final class KitsModule {

    private KitsModule() {}

    public static KitServiceImpl enable(YouNeedMe plugin, KitRepository repository) {
        KitServiceImpl service = new KitServiceImpl(repository, plugin.scheduler());
        KitLoader.load(plugin.configManager().kits()).forEach(service::register);
        plugin.getServer()
                .getServicesManager()
                .register(KitService.class, service, plugin, ServicePriority.Normal);

        KitEditorGui editorGui = new KitEditorGui(plugin);
        KitGui gui = new KitGui(plugin);
        CommandRegistrar.register(plugin, "kit", new KitCommand(plugin, gui));
        CommandRegistrar.register(plugin, "kits", new KitsCommand(plugin, editorGui));
        return service;
    }
}

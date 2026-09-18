package fr.mathildeuh.youneedme.modules.navigation;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;

public final class NavigationModule {

    private NavigationModule() {}

    public static void enable(YouNeedMe plugin) {
        CommandRegistrar.register(plugin, "back", new BackCommand(plugin));
        CommandRegistrar.register(plugin, "dback", new DBackCommand(plugin));
        CommandRegistrar.register(plugin, "top", new TopCommand(plugin));
        CommandRegistrar.register(plugin, "spawn", new SpawnCommand(plugin));
        CommandRegistrar.register(plugin, "setspawn", new SetSpawnCommand(plugin));
    }
}

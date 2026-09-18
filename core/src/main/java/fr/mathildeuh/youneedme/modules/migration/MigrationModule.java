package fr.mathildeuh.youneedme.modules.migration;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;

public final class MigrationModule {

    private MigrationModule() {}

    public static void enable(YouNeedMe plugin) {
        CommandRegistrar.register(plugin, "migration", new MigrationCommand(plugin));
    }
}

package fr.mathildeuh.youneedme.modules.admin;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;

public final class AdminModule {

    private AdminModule() {}

    public static YnmAdminCommand enable(YouNeedMe plugin) {
        YnmAdminCommand admin = new YnmAdminCommand(plugin, new AdminDashboardGui(plugin));
        CommandRegistrar.register(plugin, "ynm", admin);
        CommandRegistrar.register(plugin, "mysql", new MySqlSyncCommand(plugin));
        return admin;
    }
}

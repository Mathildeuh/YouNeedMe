package fr.mathildeuh.youneedme.command;

import fr.mathildeuh.youneedme.YouNeedMe;

/** One-line helper so every module's registration code reads the same way. */
public final class CommandRegistrar {

    private CommandRegistrar() {}

    public static void register(YouNeedMe plugin, String name, YnmCommand executor) {
        var command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger()
                    .warning(
                            "Command '"
                                    + name
                                    + "' is not declared in plugin.yml - skipping registration.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}

package fr.mathildeuh.youneedme.modules.tickets;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.storage.TicketRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import java.util.List;

public final class TicketModule {

    private TicketModule() {}

    public static TicketServiceImpl enable(YouNeedMe plugin, TicketRepository repository) {
        var config = plugin.configManager().module("tickets");
        List<String> categories = config.getStringList("categories");
        var transcriptsDir = plugin.getDataFolder().toPath().resolve("tickets");
        TicketServiceImpl service =
                new TicketServiceImpl(repository, categories, transcriptsDir, plugin.getLogger());

        TicketQueueGui gui = new TicketQueueGui(plugin);
        CommandRegistrar.register(plugin, "ticket", new TicketCommand(plugin));
        CommandRegistrar.register(plugin, "tickets", new TicketsCommand(plugin, gui));
        return service;
    }
}

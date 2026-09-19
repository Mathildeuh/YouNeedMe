package fr.mathildeuh.youneedme.modules.trade;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;

public final class TradeModule {

    private TradeModule() {}

    public static TradeManager enable(YouNeedMe plugin) {
        TradeManager manager = new TradeManager();
        TradeGui gui = new TradeGui(plugin, manager);
        CommandRegistrar.register(plugin, "trade", new TradeCommand(plugin, gui));
        CommandRegistrar.register(plugin, "tradeaccept", new TradeAcceptCommand(plugin, gui));
        CommandRegistrar.register(plugin, "tradedeny", new TradeDenyCommand(plugin));
        return manager;
    }
}

package fr.mathildeuh.youneedme.modules.shop;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.shop.ShopService;
import fr.mathildeuh.youneedme.api.storage.ShopRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

public final class ShopModule {

    private ShopModule() {}

    public static ShopServiceImpl enable(
            YouNeedMe plugin, ShopRepository repository, EconomyService economy) {
        boolean enabled = plugin.configManager().module("shop").getBoolean("enabled", true);
        var initial = ShopConfigLoader.load(plugin.configManager().shop());
        ShopServiceImpl service =
                new ShopServiceImpl(
                        repository,
                        economy,
                        initial,
                        () -> ShopConfigLoader.load(plugin.configManager().shop()),
                        enabled);
        plugin.getServer()
                .getServicesManager()
                .register(ShopService.class, service, plugin, ServicePriority.Normal);

        ShopGui gui = new ShopGui(plugin);
        ShopEditorGui editorGui = new ShopEditorGui(plugin);
        CommandRegistrar.register(plugin, "shop", new ShopCommand(plugin, gui, editorGui));
        CommandRegistrar.register(plugin, "sell", new SellCommand(plugin, gui));
        CommandRegistrar.register(plugin, "quicksell", new QuickSellCommand(plugin));
        CommandRegistrar.register(plugin, "worth", new WorthCommand(plugin));
        return service;
    }
}

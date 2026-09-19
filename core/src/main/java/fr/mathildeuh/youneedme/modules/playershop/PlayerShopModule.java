package fr.mathildeuh.youneedme.modules.playershop;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.storage.PlayerShopRepository;
import org.bukkit.Bukkit;

public final class PlayerShopModule {

    private PlayerShopModule() {}

    public static PlayerShopServiceImpl enable(YouNeedMe plugin, PlayerShopRepository repository) {
        PlayerShopServiceImpl service =
                new PlayerShopServiceImpl(
                        repository, plugin.services().economy, plugin.getLogger());
        service.load();
        Bukkit.getPluginManager().registerEvents(new PlayerShopListener(plugin), plugin);
        return service;
    }
}

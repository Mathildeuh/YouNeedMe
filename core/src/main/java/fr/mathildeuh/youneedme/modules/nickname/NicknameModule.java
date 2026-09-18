package fr.mathildeuh.youneedme.modules.nickname;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.nickname.NicknameService;
import fr.mathildeuh.youneedme.api.storage.PlayerProfileRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

public final class NicknameModule {

    private NicknameModule() {}

    public static NicknameServiceImpl enable(YouNeedMe plugin, PlayerProfileRepository repository) {
        var config = plugin.configManager().module("nickname");
        NicknameServiceImpl service =
                new NicknameServiceImpl(
                        repository,
                        config.getInt("max-length", 16),
                        config.getStringList("blacklist"));
        plugin.getServer()
                .getServicesManager()
                .register(NicknameService.class, service, plugin, ServicePriority.Normal);

        CommandRegistrar.register(plugin, "nick", new NickCommand(plugin));
        CommandRegistrar.register(plugin, "realname", new RealNameCommand(plugin));
        return service;
    }
}

package fr.mathildeuh.youneedme.modules.moderation;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.moderation.ModerationService;
import fr.mathildeuh.youneedme.api.storage.PunishmentRepository;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.plugin.ServicePriority;

public final class ModerationModule {

    private ModerationModule() {}

    public static ModerationServiceImpl enable(YouNeedMe plugin, PunishmentRepository repository) {
        ModerationServiceImpl service = new ModerationServiceImpl(repository, plugin.scheduler());
        plugin.getServer()
                .getServicesManager()
                .register(ModerationService.class, service, plugin, ServicePriority.Normal);

        CommandRegistrar.register(plugin, "ban", new BanCommand(plugin));
        CommandRegistrar.register(plugin, "ban-ip", new BanIpCommand(plugin));
        CommandRegistrar.register(plugin, "unban", new UnbanCommand(plugin));
        CommandRegistrar.register(plugin, "unban-ip", new UnbanIpCommand(plugin));
        CommandRegistrar.register(plugin, "mute", new MuteCommand(plugin));
        CommandRegistrar.register(plugin, "unmute", new UnmuteCommand(plugin));
        CommandRegistrar.register(plugin, "kick", new KickCommand(plugin));
        CommandRegistrar.register(plugin, "checkpunish", new CheckPunishCommand(plugin));
        CommandRegistrar.register(plugin, "banlist", new BanListCommand(plugin));
        CommandRegistrar.register(plugin, "smite", new SmiteCommand(plugin));
        CommandRegistrar.register(plugin, "god", new GodCommand(plugin));
        CommandRegistrar.register(plugin, "vanish", new VanishCommand(plugin));
        CommandRegistrar.register(plugin, "invsee", new InvSeeCommand(plugin));
        CommandRegistrar.register(plugin, "endersee", new EnderSeeCommand(plugin));
        CommandRegistrar.register(plugin, "sudo", new SudoCommand(plugin));
        CommandRegistrar.register(plugin, "clearinventory", new ClearInventoryCommand(plugin));
        return service;
    }
}

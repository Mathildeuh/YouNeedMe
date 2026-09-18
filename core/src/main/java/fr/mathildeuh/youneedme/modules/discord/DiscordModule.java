package fr.mathildeuh.youneedme.modules.discord;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.Bukkit;

public final class DiscordModule {

    private DiscordModule() {}

    public static void enable(YouNeedMe plugin) {
        CommandRegistrar.register(plugin, "discord", new DiscordCommand(plugin));

        var config = plugin.configManager().module("discord");
        DiscordSrvBridge discordSrv = new DiscordSrvBridge(plugin.getLogger());
        WebhookSender webhook =
                new WebhookSender(config.getString("webhook-url", ""), plugin.getLogger());
        if (discordSrv.isPresent() || webhook.isConfigured()) {
            Bukkit.getPluginManager()
                    .registerEvents(new DiscordNotifier(plugin, discordSrv, webhook), plugin);
        }
    }
}

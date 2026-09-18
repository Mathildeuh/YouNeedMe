package fr.mathildeuh.youneedme.modules.discord;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

public final class DiscordCommand extends YnmCommand {

    public DiscordCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.discord", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String invite = plugin.configManager().module("discord").getString("invite-url", "");
        if (invite.isBlank()) {
            send(sender, "discord.not_configured");
            return;
        }
        send(sender, "discord.info", Placeholder.unparsed("url", invite));
    }
}

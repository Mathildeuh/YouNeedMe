package fr.mathildeuh.youneedme.modules.nickname;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.integrations.floodgate.FloodgateHook;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class NickCommand extends YnmCommand {

    NickCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.nick", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player self = player(sender);
        Player target = self;
        String nicknameArg;
        if (args.length >= 2
                && sender.hasPermission("youneedme.nick.others")
                && Bukkit.getPlayerExact(args[0]) != null) {
            target = Bukkit.getPlayerExact(args[0]);
            nicknameArg = args[1];
        } else if (args.length >= 1) {
            nicknameArg = args[0];
        } else {
            send(sender, "command.usage.nick");
            return;
        }
        boolean removing = "off".equalsIgnoreCase(nicknameArg);
        if (!removing
                && Bukkit.getPluginManager().getPlugin("floodgate") != null
                && FloodgateHook.isBedrockPlayer(target.getUniqueId())) {
            // Bedrock clients don't render MiniMessage-style tags, so a color-coded nickname would
            // show up to them (and to other Bedrock players) as literal "<red>Name</red>" text.
            nicknameArg = nicknameArg.replaceAll("<[^>]*>", "");
        }
        boolean self1 = target.getUniqueId().equals(self.getUniqueId());
        Player finalTarget = target;
        String finalNicknameArg = nicknameArg;
        services()
                .nicknames
                .setNickname(target.getUniqueId(), removing ? null : finalNicknameArg)
                .thenAccept(
                        success -> {
                            if (!success) {
                                int maxLength =
                                        plugin.configManager()
                                                .module("nickname")
                                                .getInt("max-length", 16);
                                send(
                                        sender,
                                        "nick.error.too_long",
                                        Placeholder.unparsed("max", String.valueOf(maxLength)),
                                        Placeholder.unparsed(
                                                "current",
                                                String.valueOf(finalNicknameArg.length())));
                                return;
                            }
                            if (removing) {
                                send(self1 ? sender : finalTarget, "nick.success.reset.self");
                                if (!self1) {
                                    send(
                                            sender,
                                            "nick.success.reset.by",
                                            Placeholder.unparsed("admin", self.getName()));
                                }
                            } else {
                                send(
                                        self1 ? sender : finalTarget,
                                        "nick.success.self",
                                        Placeholder.unparsed("nick", finalNicknameArg));
                                if (!self1) {
                                    send(
                                            sender,
                                            "nick.success.set.by",
                                            Placeholder.unparsed("nick", finalNicknameArg),
                                            Placeholder.unparsed("admin", self.getName()));
                                }
                            }
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("off") : List.of();
    }
}

final class RealNameCommand extends YnmCommand {

    RealNameCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.realname", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "command.usage.realname");
            return;
        }
        var uuid = services().nicknames.resolveRealPlayer(args[0]);
        if (uuid.isEmpty()) {
            send(sender, "realname.error.not_found");
            return;
        }
        String realName = Bukkit.getOfflinePlayer(uuid.get()).getName();
        send(
                sender,
                "realname.success",
                Placeholder.unparsed("nick", args[0]),
                Placeholder.unparsed("real", String.valueOf(realName)));
    }
}

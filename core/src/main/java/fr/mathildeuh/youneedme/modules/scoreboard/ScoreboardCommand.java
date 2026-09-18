package fr.mathildeuh.youneedme.modules.scoreboard;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ScoreboardCommand extends YnmCommand {

    public ScoreboardCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.scoreboard.toggle", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "toggle";
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("youneedme.admin")) {
                send(sender, "error.no_permission");
                return;
            }
            services().scoreboard.reload();
            send(sender, "scoreboard.reloaded");
            return;
        }
        services().scoreboard.toggle(player.getUniqueId());
        send(
                sender,
                services().scoreboard.isEnabledFor(player.getUniqueId())
                        ? "scoreboard.enabled"
                        : "scoreboard.disabled");
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("toggle", "reload") : List.of();
    }
}

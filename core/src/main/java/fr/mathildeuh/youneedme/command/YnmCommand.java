package fr.mathildeuh.youneedme.command;

import fr.mathildeuh.youneedme.YouNeedMe;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared plumbing for every YouNeedMe command: permission gate, player-only gate, uniform error
 * messages and uncaught-exception handling so a bug in one command can never dump a stack trace
 * into a player's chat.
 */
public abstract class YnmCommand implements CommandExecutor, TabCompleter {

    protected final YouNeedMe plugin;
    private final String permission;
    private final boolean playerOnly;

    protected YnmCommand(YouNeedMe plugin, @Nullable String permission, boolean playerOnly) {
        this.plugin = plugin;
        this.permission = permission;
        this.playerOnly = playerOnly;
    }

    @Override
    public final boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (playerOnly && !(sender instanceof Player)) {
            send(sender, "error.player_only");
            return true;
        }
        if (permission != null && !sender.hasPermission(permission)) {
            send(sender, "error.no_permission");
            return true;
        }
        try {
            execute(sender, label, args);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unhandled error executing /" + label, e);
            send(sender, "error.internal");
        }
        return true;
    }

    protected abstract void execute(CommandSender sender, String label, String[] args);

    @Override
    public final List<String> onTabComplete(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (permission != null && !sender.hasPermission(permission)) {
            return List.of();
        }
        try {
            return tabComplete(sender, args);
        } catch (Exception e) {
            return List.of();
        }
    }

    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    protected final Player player(CommandSender sender) {
        return (Player) sender;
    }

    protected final void send(CommandSender sender, String key, TagResolver... placeholders) {
        sender.sendMessage(plugin.lang().render(sender, key, placeholders));
    }

    protected final void broadcast(String key, TagResolver... placeholders) {
        plugin.getServer().sendMessage(plugin.lang().render(plugin.getServer().getConsoleSender(), key, placeholders));
    }
}

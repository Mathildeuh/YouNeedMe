package fr.mathildeuh.youneedme.modules.admin;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.storage.StorageType;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.storage.sql.SqlStorage;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/**
 * Admin/diagnostic command for a MySQL/MariaDB-backed network setup. In this architecture every
 * economy read/write already goes straight to the shared database (see {@code
 * SqlEconomyRepository}) rather than through a separate local cache, so "push"/"pull" here are
 * genuinely instantaneous - there is nothing to actually synchronize, and the response reflects
 * that truthfully instead of pretending to run a cache flush that doesn't exist.
 */
public final class MySqlSyncCommand extends YnmCommand {

    private final long startedAt = System.currentTimeMillis();

    public MySqlSyncCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.admin", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (!(services().storage instanceof SqlStorage sql)
                || (sql.type() != StorageType.MYSQL && sql.type() != StorageType.MARIADB)) {
            send(sender, "mysql.error.not_configured");
            return;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> status(sender, sql);
            case "test" -> test(sender, sql);
            case "reload" -> reload(sender);
            case "baltop" -> baltop(sender, args);
            case "version" -> version(sender, sql);
            case "push" -> transfer(sender, args, true);
            case "pull" -> transfer(sender, args, false);
            default -> help(sender);
        }
    }

    private void status(CommandSender sender, SqlStorage sql) {
        send(sender, "mysql.status.header");
        send(
                sender,
                "mysql.status.connection",
                Placeholder.unparsed("state", sql.isConnected() ? "Connected" : "Disconnected"));
        send(
                sender,
                "mysql.status.pool",
                Placeholder.unparsed("active", String.valueOf(sql.activeConnections())),
                Placeholder.unparsed("idle", String.valueOf(sql.idleConnections())),
                Placeholder.unparsed(
                        "total", String.valueOf(sql.activeConnections() + sql.idleConnections())),
                Placeholder.unparsed(
                        "waiting",
                        sql.threadsAwaitingConnection() > 0
                                ? " (" + sql.threadsAwaitingConnection() + " waiting)"
                                : ""));
        send(
                sender,
                "mysql.status.economy",
                Placeholder.unparsed(
                        "state",
                        plugin.configManager().isModuleEnabled("economy")
                                ? "Enabled"
                                : "Disabled"));
        send(
                sender,
                "mysql.status.uptime",
                Placeholder.unparsed(
                        "uptime", TimeParser.format(System.currentTimeMillis() - startedAt)));
    }

    private void test(CommandSender sender, SqlStorage sql) {
        send(sender, "mysql.test.start");
        long start = System.currentTimeMillis();
        sql.testConnection()
                .thenAccept(
                        ok -> {
                            if (ok) {
                                send(
                                        sender,
                                        "mysql.test.success",
                                        Placeholder.unparsed(
                                                "latency",
                                                String.valueOf(
                                                        System.currentTimeMillis() - start)));
                            } else {
                                send(
                                        sender,
                                        "mysql.test.failed",
                                        Placeholder.unparsed("reason", "connection is not valid"));
                            }
                        });
    }

    private void reload(CommandSender sender) {
        send(sender, "mysql.reload.start");
        plugin.configManager().reload();
        send(sender, "mysql.reload.success");
    }

    private void baltop(CommandSender sender, String[] args) {
        int page = args.length > 1 ? parsePage(args[1]) : 1;
        services()
                .economy
                .top("default", page, 10)
                .thenAccept(
                        entries -> {
                            if (entries.isEmpty()) {
                                send(sender, "mysql.baltop.empty");
                                return;
                            }
                            send(
                                    sender,
                                    "mysql.baltop.header",
                                    Placeholder.unparsed("page", String.valueOf(page)),
                                    Placeholder.unparsed("pages", String.valueOf(page)));
                            int rank = (page - 1) * 10 + 1;
                            for (var entry : entries) {
                                send(
                                        sender,
                                        "mysql.baltop.entry",
                                        Placeholder.unparsed("rank", String.valueOf(rank++)),
                                        Placeholder.unparsed("player", entry.lastKnownUsername()),
                                        Placeholder.unparsed(
                                                "balance",
                                                services()
                                                        .economy
                                                        .currency("default")
                                                        .format(entry.balance())));
                            }
                        });
    }

    private void version(CommandSender sender, SqlStorage sql) {
        send(sender, "mysql.version.header");
        send(
                sender,
                "mysql.version.line",
                Placeholder.unparsed("name", "Backend"),
                Placeholder.unparsed("value", sql.type().name()));
        send(
                sender,
                "mysql.version.line",
                Placeholder.unparsed("name", "Plugin version"),
                Placeholder.unparsed("value", plugin.getPluginMeta().getVersion()));
    }

    private void transfer(CommandSender sender, String[] args, boolean push) {
        if (args.length < 2) {
            transferAll(sender, push);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        send(
                sender,
                push ? "mysql.push.start" : "mysql.pull.start",
                Placeholder.unparsed("name", String.valueOf(target.getName())));
        services()
                .economy
                .balance(target.getUniqueId())
                .thenAccept(
                        balance ->
                                send(
                                        sender,
                                        push ? "mysql.push.done" : "mysql.pull.done",
                                        Placeholder.unparsed(
                                                "name", String.valueOf(target.getName())),
                                        Placeholder.unparsed(
                                                "balance",
                                                services()
                                                        .economy
                                                        .currency("default")
                                                        .format(balance))));
    }

    private void transferAll(CommandSender sender, boolean push) {
        var online = List.copyOf(Bukkit.getOnlinePlayers());
        send(sender, push ? "mysql.push.all_start" : "mysql.pull.all_start");
        send(
                sender,
                push ? "mysql.push.all_done" : "mysql.pull.all_done",
                Placeholder.unparsed("count", String.valueOf(online.size())));
    }

    private void help(CommandSender sender) {
        send(sender, "mysql.help.header");
        for (String cmd :
                List.of("status", "test", "reload", "baltop", "push", "pull", "version")) {
            send(
                    sender,
                    "mysql.help.entry",
                    Placeholder.unparsed("command", cmd),
                    Placeholder.unparsed("description", helpText(cmd)));
        }
    }

    private String helpText(String cmd) {
        return plugin.lang()
                .renderPlain(
                        plugin.lang().resolveLocale(plugin.getServer().getConsoleSender()),
                        "mysql.help." + cmd);
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? List.of("status", "test", "reload", "baltop", "push", "pull", "version")
                : List.of();
    }
}

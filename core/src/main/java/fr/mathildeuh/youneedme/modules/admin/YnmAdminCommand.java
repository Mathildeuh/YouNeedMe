package fr.mathildeuh.youneedme.modules.admin;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

public final class YnmAdminCommand extends YnmCommand {

    private final AtomicBoolean debugMode = new AtomicBoolean(false);

    public YnmAdminCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.admin", false);
    }

    public boolean isDebug() {
        return debugMode.get();
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "reload" -> reload(sender);
            case "version" ->
                    send(
                            sender,
                            "ynm.version",
                            Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()));
            case "debug" -> {
                boolean now = !debugMode.get();
                debugMode.set(now);
                send(
                        sender,
                        "ynm.debug.toggled",
                        Placeholder.unparsed("state", now ? "ON" : "OFF"));
            }
            case "backup" -> backup(sender, args);
            case "dump" -> dump(sender);
            case "placeholders" -> placeholders(sender);
            case "commands" -> commandsList(sender);
            default -> help(sender);
        }
    }

    private void reload(CommandSender sender) {
        plugin.configManager().reload();
        plugin.lang()
                .load(
                        plugin.getDataFolder().toPath().resolve("lang"),
                        plugin.configManager().main().getString("language.default", "en_US"));
        services().warps.reload();
        services().shop.reload();
        send(sender, "ynm.reload.success");
    }

    private void help(CommandSender sender) {
        send(sender, "ynm.help.header");
        send(sender, "ynm.help.reload");
        send(sender, "ynm.help.version");
        send(sender, "ynm.help.debug");
        send(sender, "ynm.help.backup");
        send(sender, "ynm.help.placeholders", Placeholder.unparsed("page", "1"));
        send(sender, "ynm.help.commands", Placeholder.unparsed("page", "1"));
        send(sender, "ynm.help.wiki");
    }

    private void dump(CommandSender sender) {
        Path file =
                plugin.getDataFolder()
                        .toPath()
                        .resolve("dump-" + System.currentTimeMillis() + ".txt");
        StringBuilder builder = new StringBuilder();
        builder.append("YouNeedMe version: ")
                .append(plugin.getPluginMeta().getVersion())
                .append('\n');
        builder.append("Server: ").append(plugin.getServer().getVersion()).append('\n');
        builder.append("Storage: ").append(services().storage.type()).append('\n');
        builder.append("Online players: ")
                .append(plugin.getServer().getOnlinePlayers().size())
                .append('\n');
        try {
            Files.writeString(file, builder.toString());
            send(sender, "dump.saved", Placeholder.unparsed("file", file.getFileName().toString()));
        } catch (IOException e) {
            send(sender, "dump.error.write");
        }
    }

    private void placeholders(CommandSender sender) {
        List<String> known =
                List.of(
                        "%youneedme_balance%",
                        "%youneedme_homes_count%",
                        "%youneedme_language%",
                        "%youneedme_nickname%",
                        "%youneedme_afk%",
                        "%youneedme_vanished%");
        send(
                sender,
                "ynm.placeholders.header",
                Placeholder.unparsed("page", "1"),
                Placeholder.unparsed("total_pages", "1"));
        known.forEach(p -> sender.sendMessage(net.kyori.adventure.text.Component.text(" - " + p)));
        send(
                sender,
                "ynm.placeholders.footer",
                Placeholder.unparsed("count", String.valueOf(known.size())));
    }

    private void commandsList(CommandSender sender) {
        var commands = plugin.getDescription().getCommands().keySet().stream().sorted().toList();
        send(
                sender,
                "ynm.commands.header",
                Placeholder.unparsed("page", "1"),
                Placeholder.unparsed("total_pages", "1"));
        commands.forEach(
                c -> sender.sendMessage(net.kyori.adventure.text.Component.text(" - /" + c)));
        send(
                sender,
                "ynm.commands.footer",
                Placeholder.unparsed("count", String.valueOf(commands.size())));
    }

    private void backup(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "create";
        Path backupsDir = plugin.getDataFolder().toPath().resolve("backups");
        switch (action) {
            case "list" -> {
                try {
                    Files.createDirectories(backupsDir);
                    var backups =
                            Files.list(backupsDir)
                                    .filter(p -> p.toString().endsWith(".zip"))
                                    .toList();
                    if (backups.isEmpty()) {
                        send(sender, "ynm.backup.list.empty");
                        return;
                    }
                    send(
                            sender,
                            "ynm.backup.list.header",
                            Placeholder.unparsed("count", String.valueOf(backups.size())));
                    for (Path backup : backups) {
                        send(
                                sender,
                                "ynm.backup.list.entry",
                                Placeholder.unparsed("name", backup.getFileName().toString()),
                                Placeholder.unparsed("size", humanSize(backup.toFile().length())),
                                Placeholder.unparsed(
                                        "date",
                                        DateTimeFormatter.ISO_INSTANT.format(
                                                Files.getLastModifiedTime(backup).toInstant())));
                    }
                } catch (IOException e) {
                    send(
                            sender,
                            "ynm.backup.create.failed",
                            Placeholder.unparsed("error", e.getMessage()));
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    send(sender, "ynm.backup.delete.usage");
                    return;
                }
                Path target = backupsDir.resolve(args[2]);
                try {
                    if (Files.deleteIfExists(target)) {
                        send(
                                sender,
                                "ynm.backup.delete.success",
                                Placeholder.unparsed("name", args[2]));
                    } else {
                        send(
                                sender,
                                "ynm.backup.delete.not_found",
                                Placeholder.unparsed("name", args[2]));
                    }
                } catch (IOException e) {
                    send(
                            sender,
                            "ynm.backup.delete.not_found",
                            Placeholder.unparsed("name", args[2]));
                }
            }
            default -> createBackup(sender, backupsDir);
        }
    }

    private void createBackup(CommandSender sender, Path backupsDir) {
        send(sender, "ynm.backup.create.starting");
        plugin.scheduler()
                .runAsync(
                        () -> {
                            try {
                                Files.createDirectories(backupsDir);
                                String name = "backup-" + System.currentTimeMillis() + ".zip";
                                Path zipPath = backupsDir.resolve(name);
                                try (ZipOutputStream zip =
                                        new ZipOutputStream(
                                                new FileOutputStream(zipPath.toFile()))) {
                                    File dataFolder = plugin.getDataFolder();
                                    zipDirectory(zip, dataFolder, dataFolder.toPath(), backupsDir);
                                }
                                plugin.scheduler()
                                        .runGlobal(
                                                () ->
                                                        send(
                                                                sender,
                                                                "ynm.backup.create.success",
                                                                Placeholder.unparsed(
                                                                        "name", name)));
                            } catch (IOException e) {
                                plugin.scheduler()
                                        .runGlobal(
                                                () ->
                                                        send(
                                                                sender,
                                                                "ynm.backup.create.failed",
                                                                Placeholder.unparsed(
                                                                        "error", e.getMessage())));
                            }
                        });
    }

    private static void zipDirectory(ZipOutputStream zip, File current, Path base, Path exclude)
            throws IOException {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.toPath().startsWith(exclude)) {
                continue;
            }
            if (file.isDirectory()) {
                zipDirectory(zip, file, base, exclude);
            } else {
                zip.putNextEntry(
                        new ZipEntry(base.relativize(file.toPath()).toString().replace('\\', '/')));
                Files.copy(file.toPath(), zip);
                zip.closeEntry();
            }
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGT".charAt(exp - 1) + "B";
        return String.format(Locale.ROOT, "%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of(
                    "dump",
                    "reload",
                    "backup",
                    "version",
                    "debug",
                    "placeholders",
                    "commands",
                    "help");
        }
        if (args.length == 2 && "backup".equalsIgnoreCase(args[0])) {
            return List.of("create", "list", "delete");
        }
        return List.of();
    }
}

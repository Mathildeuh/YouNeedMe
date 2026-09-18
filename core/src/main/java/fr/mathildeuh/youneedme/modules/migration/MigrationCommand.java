package fr.mathildeuh.youneedme.modules.migration;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * Entry point for {@code /migration <source> [--apply]}, in the priority order the brief lists:
 * EssentialsX (by far the most common source), CMI, then EssentialsC.
 */
public final class MigrationCommand extends YnmCommand {

    public MigrationCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.migration", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(
                    Component.text("Usage: /migration <essentialsx|cmi|essentialsc> [--apply]"));
            return;
        }
        String source = args[0].toLowerCase(java.util.Locale.ROOT);
        boolean apply = args.length > 1 && "--apply".equalsIgnoreCase(args[1]);
        CompletableFuture<MigrationReport> report =
                switch (source) {
                    case "essentialsx" -> new EssentialsXImporter(plugin).run(apply);
                    case "cmi" -> new CmiImporter(plugin).run(apply);
                    case "essentialsc" -> new EssentialsCImporter(plugin).run(apply);
                    default -> null;
                };
        if (report == null) {
            sender.sendMessage(
                    Component.text(
                            "Unknown migration source '"
                                    + source
                                    + "' - use essentialsx, cmi or essentialsc."));
            return;
        }
        sender.sendMessage(
                Component.text(
                        "Starting "
                                + source
                                + " migration ("
                                + (apply ? "APPLY" : "DRY-RUN")
                                + ")..."));
        report.thenAccept(
                result -> {
                    sender.sendMessage(Component.text(result.summary()));
                    if (!apply) {
                        sender.sendMessage(
                                Component.text(
                                        "This was a dry-run - nothing was written. Re-run with"
                                                + " --apply to import for real."));
                    }
                });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("essentialsx", "cmi", "essentialsc");
        }
        if (args.length == 2) {
            return List.of("--apply");
        }
        return List.of();
    }
}

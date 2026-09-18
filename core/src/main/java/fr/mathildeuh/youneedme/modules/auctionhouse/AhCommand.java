package fr.mathildeuh.youneedme.modules.auctionhouse;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /ah} opens the auction house menu directly - every action (sell/cancel/claim/browse) is a
 * GUI click, not a subcommand.
 */
public final class AhCommand extends YnmCommand {

    private final AhGui gui;

    public AhCommand(YouNeedMe plugin, AhGui gui) {
        super(plugin, "youneedme.ah.use", true);
        this.gui = gui;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        gui.openMenu(player);
    }
}

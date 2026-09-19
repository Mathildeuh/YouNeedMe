package fr.mathildeuh.youneedme.modules.scoreboard;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.util.PapiHook;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Renders the YAML-configured sidebar scoreboard on a repeating timer. Yields entirely to TAB when
 * it's installed and its own scoreboard feature is enabled (see {@link
 * ScoreboardServiceImpl#isActive()}), since two plugins fighting over the sidebar slot just
 * flickers for the player.
 */
public final class ScoreboardRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String OBJECTIVE_ID = "ynm_board";

    private final YouNeedMe plugin;
    private final ScoreboardServiceImpl service;

    public ScoreboardRenderer(YouNeedMe plugin, ScoreboardServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start() {
        var config = plugin.configManager().module("scoreboard");
        long interval = Math.max(1, config.getLong("refresh-interval-ticks", 20));
        plugin.scheduler().runGlobalTimer(this::renderAll, interval, interval);
    }

    private void renderAll() {
        if (!service.isActive()) {
            return;
        }
        var config = plugin.configManager().module("scoreboard");
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        String title = config.getString("title", "<gold><bold>YouNeedMe");
        List<String> lines = config.getStringList("lines");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (service.isEnabledFor(player.getUniqueId())) {
                render(player, title, lines);
            } else {
                clear(player);
            }
        }
    }

    private void render(Player player, String title, List<String> lines) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(scoreboard);
        }
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_ID, "dummy", Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(resolve(player, title));

        int lineCount = Math.min(lines.size(), ChatColor.values().length);
        for (int i = 0; i < lineCount; i++) {
            String entry = ChatColor.values()[i].toString();
            Team team = scoreboard.getTeam("ynm_l" + i);
            if (team == null) {
                team = scoreboard.registerNewTeam("ynm_l" + i);
                team.addEntry(entry);
            }
            team.prefix(resolve(player, lines.get(i)));
            objective.getScore(entry).setScore(lineCount - i);
        }
        for (String entry : List.copyOf(scoreboard.getEntries())) {
            boolean stillUsed = false;
            for (int i = 0; i < lineCount; i++) {
                if (entry.equals(ChatColor.values()[i].toString())) {
                    stillUsed = true;
                    break;
                }
            }
            if (!stillUsed) {
                scoreboard.resetScores(entry);
            }
        }
    }

    private Component resolve(Player player, String raw) {
        String withCount =
                raw.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                        .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                        .replace("{server}", Bukkit.getServer().getName())
                        .replace("{player}", player.getName());
        return MINI_MESSAGE.deserialize(
                PapiHook.apply(player, withCount),
                Placeholder.unparsed("player", player.getName()));
    }

    private void clear(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard.getObjective(OBJECTIVE_ID) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}

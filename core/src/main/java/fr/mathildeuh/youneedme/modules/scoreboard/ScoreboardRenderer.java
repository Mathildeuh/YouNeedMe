package fr.mathildeuh.youneedme.modules.scoreboard;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.util.PapiHook;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the YAML-configured sidebar scoreboard on a repeating timer. Yields entirely to TAB when
 * it's installed and its own scoreboard feature is enabled (see {@link
 * ScoreboardServiceImpl#isActive()}), since two plugins fighting over the sidebar slot just
 * flickers for the player.
 */
public final class ScoreboardRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String OBJECTIVE_ID = "ynm_board";
    // Unique, invisible-when-rendered legacy formatting codes used as scoreboard line "fake player"
    // entries - a Team's prefix does the actual line rendering, this only needs to be a stable,
    // distinct token per line. Inlined instead of org.bukkit.ChatColor#values() (deprecated) since
    // that enum exists purely to enumerate these same code points anyway.
    private static final String[] LINE_TOKENS = {
        "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e",
        "§f", "§k", "§l", "§m", "§n", "§o", "§r"
    };

    private final YouNeedMe plugin;
    private final ScoreboardServiceImpl service;
    // Advances by one every renderAll() call (not a raw server tick count) - frame selection below
    // divides this by framesPerAdvance so animation speed stays in step with the actual refresh
    // rate instead of drifting against it.
    private long renderTick;

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
        renderTick++;
        long refreshIntervalTicks = Math.max(1, config.getLong("refresh-interval-ticks", 20));
        long animationIntervalTicks =
                Math.max(1, config.getLong("animation-interval-ticks", refreshIntervalTicks));
        long framesPerAdvance = Math.max(1, animationIntervalTicks / refreshIntervalTicks);
        int frame = (int) ((renderTick / framesPerAdvance) % Integer.MAX_VALUE);

        Object titleRaw = config.get("title", "<gold><bold>YouNeedMe");
        List<?> linesRaw = config.getList("lines", List.of());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (service.isEnabledFor(player.getUniqueId())) {
                render(player, titleRaw, linesRaw, frame);
            } else {
                clear(player);
            }
        }
    }

    private void render(Player player, Object titleRaw, List<?> linesRaw, int frame) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(scoreboard);
        }
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective =
                    scoreboard.registerNewObjective(
                            OBJECTIVE_ID, Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(resolve(player, currentFrame(titleRaw, frame)));

        int lineCount = Math.min(linesRaw.size(), LINE_TOKENS.length);
        for (int i = 0; i < lineCount; i++) {
            String entry = LINE_TOKENS[i];
            Team team = scoreboard.getTeam("ynm_l" + i);
            if (team == null) {
                team = scoreboard.registerNewTeam("ynm_l" + i);
                team.addEntry(entry);
            }
            team.prefix(resolve(player, currentFrame(linesRaw.get(i), frame)));
            objective.getScore(entry).setScore(lineCount - i);
        }
        for (String entry : List.copyOf(scoreboard.getEntries())) {
            boolean stillUsed = false;
            for (int i = 0; i < lineCount; i++) {
                if (entry.equals(LINE_TOKENS[i])) {
                    stillUsed = true;
                    break;
                }
            }
            if (!stillUsed) {
                scoreboard.resetScores(entry);
            }
        }
    }

    /**
     * A title or line entry is either a single static string, or a YAML list of strings to cycle
     * through as animation frames - {@code frame} picks which one is currently shown.
     */
    private static String currentFrame(@Nullable Object raw, int frame) {
        if (raw instanceof List<?> frames && !frames.isEmpty()) {
            Object selected = frames.get(Math.floorMod(frame, frames.size()));
            return selected == null ? "" : selected.toString();
        }
        return raw == null ? "" : raw.toString();
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

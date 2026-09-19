package fr.mathildeuh.youneedme.modules.scoreboard;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.util.PapiHook;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
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
    // Advances by one every renderAll() call (not a raw server tick count), so frame selection
    // stays in step with the actual refresh rate instead of drifting against it.
    private long renderTick;
    private final Map<Player, UUID> managedPlayerObjects = new IdentityHashMap<>();
    private final Map<UUID, Scoreboard> managedScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private YamlConfiguration parsedConfig;
    private ScoreboardAnimation title;
    private List<ScoreboardAnimation> lines = List.of();
    private long refreshIntervalTicks;
    private long defaultAnimationIntervalTicks;

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
        pruneOfflinePlayers();
        if (!service.isActive()) {
            clearAll();
            return;
        }
        var config = plugin.configManager().module("scoreboard");
        if (!config.getBoolean("enabled", true)) {
            clearAll();
            return;
        }
        renderTick++;
        refreshSettings(config);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (service.isEnabledFor(player.getUniqueId())) {
                render(player, title, lines, refreshIntervalTicks);
            } else {
                clear(player);
            }
        }
    }

    private void refreshSettings(YamlConfiguration config) {
        if (Objects.equals(config, parsedConfig)) {
            return;
        }
        refreshIntervalTicks = Math.max(1, config.getLong("refresh-interval-ticks", 20));
        defaultAnimationIntervalTicks =
                Math.max(1, config.getLong("animation-interval-ticks", refreshIntervalTicks));
        title =
                ScoreboardAnimation.parse(
                        config.get("title", "<gold><bold>YouNeedMe"),
                        defaultAnimationIntervalTicks);
        lines =
                config.getList("lines", List.of()).stream()
                        .map(raw -> ScoreboardAnimation.parse(raw, defaultAnimationIntervalTicks))
                        .toList();
        parsedConfig = config;
    }

    private void render(
            Player player,
            ScoreboardAnimation title,
            List<ScoreboardAnimation> lines,
            long refreshIntervalTicks) {
        UUID playerId = player.getUniqueId();
        Scoreboard scoreboard = managedScoreboards.get(playerId);
        if (scoreboard != null && !managedPlayerObjects.containsKey(player)) {
            // A reconnect creates a new Player object but keeps the UUID. The old scoreboard
            // belongs to the previous session and must not be reused.
            managedPlayerObjects
                    .entrySet()
                    .removeIf(entry -> Objects.equals(entry.getValue(), playerId));
            managedScoreboards.remove(playerId);
            previousScoreboards.remove(playerId);
            scoreboard = managedScoreboards.get(playerId);
        }
        if (scoreboard == null) {
            previousScoreboards.put(playerId, player.getScoreboard());
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            managedPlayerObjects.put(player, playerId);
            managedScoreboards.put(playerId, scoreboard);
            player.setScoreboard(scoreboard);
        } else if (!Objects.equals(player.getScoreboard(), scoreboard)) {
            // Another plugin took ownership after we installed ours. Do not fight it or modify
            // its entries; clear() will also leave that newer scoreboard untouched.
            return;
        }
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective =
                    scoreboard.registerNewObjective(
                            OBJECTIVE_ID, Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(resolve(player, title.frameAt(renderTick, refreshIntervalTicks)));

        int lineCount = Math.min(lines.size(), LINE_TOKENS.length);
        for (int i = 0; i < lineCount; i++) {
            String entry = LINE_TOKENS[i];
            Team team = scoreboard.getTeam("ynm_l" + i);
            if (team == null) {
                team = scoreboard.registerNewTeam("ynm_l" + i);
                team.addEntry(entry);
            }
            team.prefix(resolve(player, lines.get(i).frameAt(renderTick, refreshIntervalTicks)));
            objective.getScore(entry).setScore(lineCount - i);
        }
        for (int i = lineCount; i < LINE_TOKENS.length; i++) {
            scoreboard.resetScores(LINE_TOKENS[i]);
            Team team = scoreboard.getTeam("ynm_l" + i);
            if (team != null) {
                team.unregister();
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
        UUID playerId = player.getUniqueId();
        managedPlayerObjects
                .entrySet()
                .removeIf(entry -> Objects.equals(entry.getValue(), playerId));
        Scoreboard managed = managedScoreboards.remove(playerId);
        Scoreboard previous = previousScoreboards.remove(playerId);
        if (managed != null && Objects.equals(player.getScoreboard(), managed)) {
            player.setScoreboard(
                    previous == null
                            ? Bukkit.getScoreboardManager().getMainScoreboard()
                            : previous);
        }
    }

    private void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    private void pruneOfflinePlayers() {
        managedPlayerObjects
                .entrySet()
                .removeIf(entry -> Bukkit.getPlayer(entry.getValue()) == null);
        managedScoreboards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        previousScoreboards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}

package fr.mathildeuh.youneedme.modules.nickname;

import fr.mathildeuh.youneedme.scheduler.ServerEnvironment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

/**
 * Pushes a nickname as far into the client-visible surface as the plain Bukkit/Paper API allows:
 * tab list name, on top of the chat format {@link
 * fr.mathildeuh.youneedme.listener.PlayerLifecycleListener} already applies, plus hiding the
 * above-head floating nametag (which shows the real username, not the tab-list name).
 *
 * <p>{@code Entity#customName()}/{@code setCustomNameVisible()} have no effect on a real player's
 * floating nametag - that only applies to non-player entities. Since the 1.19 chat-signing changes,
 * the client always renders a player's own profile name above their head; a scoreboard team can
 * only wrap it in a prefix/suffix, never replace the text in between. Hiding the tag entirely (this
 * class) is the honest ceiling reachable without a disguise-style companion entity standing in for
 * the player, which is a materially different feature than a nickname toggle.
 *
 * <p>The real username still appears in server logs, console output, most other plugins' player
 * lookups, and the vanilla tab-completion of usernames, since those all read the account's {@code
 * GameProfile} directly rather than anything Bukkit exposes as overridable.
 */
public final class NicknameDisplay {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String TEAM_PREFIX = "ynm_nick_";

    private NicknameDisplay() {}

    public static void apply(Player player, @Nullable String nickname) {
        if (nickname == null) {
            player.playerListName(null);
            if (!ServerEnvironment.isFolia()) {
                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                Team team = board.getTeam(TEAM_PREFIX + player.getUniqueId());
                if (team != null) {
                    team.unregister();
                }
            }
            return;
        }
        Component rendered = MINI_MESSAGE.deserialize(nickname);
        player.playerListName(rendered);

        // Folia currently throws from every Bukkit scoreboard operation. The tab-list nickname
        // remains supported, but hiding the above-head name requires a scoreboard team and is
        // therefore unavailable on Folia.
        if (ServerEnvironment.isFolia()) {
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM_PREFIX + player.getUniqueId());
        if (team == null) {
            team = board.registerNewTeam(TEAM_PREFIX + player.getUniqueId());
        }
        team.addEntry(player.getName());
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }
}

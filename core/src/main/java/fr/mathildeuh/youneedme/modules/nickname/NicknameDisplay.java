package fr.mathildeuh.youneedme.modules.nickname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Pushes a nickname as far into the client-visible surface as the plain Bukkit/Paper API allows:
 * tab list name and above-head nametag, on top of the chat format {@link
 * fr.mathildeuh.youneedme.listener.PlayerLifecycleListener} already applies.
 *
 * <p>This is a genuine limit, not an oversight: the player's real username still appears in server
 * logs, console output, most other plugins' player lookups, and the vanilla tab-completion of
 * usernames, since those all read the account's {@code GameProfile} directly rather than anything
 * Bukkit exposes as overridable. Fully hiding it everywhere would mean spoofing the profile at the
 * protocol level (a disguise plugin's job, not a nickname feature) - out of scope here.
 */
public final class NicknameDisplay {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private NicknameDisplay() {}

    public static void apply(Player player, @Nullable String nickname) {
        if (nickname == null) {
            player.playerListName(null);
            player.customName(null);
            player.setCustomNameVisible(false);
            return;
        }
        Component rendered = MINI_MESSAGE.deserialize(nickname);
        player.playerListName(rendered);
        player.customName(rendered);
        player.setCustomNameVisible(true);
    }
}

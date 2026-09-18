package fr.mathildeuh.youneedme.modules.discord;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

/**
 * Talks to DiscordSRV purely via reflection: no compile-time dependency on DiscordSRV/JDA, so the
 * build never needs that (heavy) jar just to soft-depend on it. No-ops entirely when DiscordSRV
 * isn't installed.
 */
public final class DiscordSrvBridge {

    private final Logger logger;
    private final boolean present;
    private Object discordSrvInstance;
    private Method getMainTextChannelMethod;
    private Method sendMessageMethod;

    public DiscordSrvBridge(Logger logger) {
        this.logger = logger;
        this.present = Bukkit.getPluginManager().getPlugin("DiscordSRV") != null;
        if (present) {
            initialize();
        }
    }

    public boolean isPresent() {
        return present;
    }

    private void initialize() {
        try {
            Class<?> discordSrvClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Object plugin = discordSrvClass.getMethod("getPlugin").invoke(null);
            this.discordSrvInstance = plugin;
            this.getMainTextChannelMethod = discordSrvClass.getMethod("getMainTextChannel");
        } catch (ReflectiveOperationException e) {
            logger.info(
                    "DiscordSRV detected but its API shape wasn't recognized (version mismatch?) -"
                            + " falling back to webhooks only.");
        }
    }

    public void sendToMainChannel(String message) {
        if (!present || discordSrvInstance == null || getMainTextChannelMethod == null) {
            return;
        }
        try {
            Object channel = getMainTextChannelMethod.invoke(discordSrvInstance);
            if (channel == null) {
                return;
            }
            if (sendMessageMethod == null) {
                sendMessageMethod = channel.getClass().getMethod("sendMessage", CharSequence.class);
            }
            Object action = sendMessageMethod.invoke(channel, message);
            action.getClass().getMethod("queue").invoke(action);
        } catch (ReflectiveOperationException e) {
            logger.fine("Could not relay message to DiscordSRV: " + e.getMessage());
        }
    }
}

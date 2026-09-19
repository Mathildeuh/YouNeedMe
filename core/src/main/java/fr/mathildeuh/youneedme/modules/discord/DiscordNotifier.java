package fr.mathildeuh.youneedme.modules.discord;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.event.PunishmentIssuedEvent;
import fr.mathildeuh.youneedme.api.event.TicketCreateEvent;
import fr.mathildeuh.youneedme.api.moderation.PunishmentType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Relays bans/mutes/kicks and join/quit events to Discord: DiscordSRV when it's installed (per the
 * brief, it takes priority), otherwise a plain webhook. Each category is independently toggleable
 * in {@code modules/discord.yml}.
 */
public final class DiscordNotifier implements Listener {

    private final YouNeedMe plugin;
    private final DiscordSrvBridge discordSrv;
    private final WebhookSender webhook;

    public DiscordNotifier(YouNeedMe plugin, DiscordSrvBridge discordSrv, WebhookSender webhook) {
        this.plugin = plugin;
        this.discordSrv = discordSrv;
        this.webhook = webhook;
    }

    private YamlConfiguration config() {
        return plugin.configManager().module("discord");
    }

    private void send(String message) {
        if (discordSrv.isPresent()) {
            discordSrv.sendToMainChannel(message);
        } else {
            webhook.send(message);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPunishment(PunishmentIssuedEvent event) {
        var punishment = event.getPunishment();
        String key =
                switch (punishment.type()) {
                    case BAN, IP_BAN -> "notify.bans";
                    case MUTE -> "notify.mutes";
                    case KICK -> "notify.kicks";
                    case WARN -> "notify.warns";
                };
        if (!config().getBoolean(key, true)) {
            return;
        }
        var targetId = punishment.target();
        String who =
                punishment.type() == PunishmentType.IP_BAN
                        ? punishment.targetIp()
                        : targetId == null
                                ? null
                                : org.bukkit.Bukkit.getOfflinePlayer(targetId).getName();
        send(
                ":hammer: **"
                        + punishment.type().name()
                        + "** `"
                        + who
                        + "` - "
                        + punishment.reason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTicketCreate(TicketCreateEvent event) {
        if (!config().getBoolean("notify.tickets", true)) {
            return;
        }
        var ticket = event.getTicket();
        send(
                ":ticket: **New ticket #"
                        + ticket.id()
                        + "** from `"
                        + ticket.playerLastKnownUsername()
                        + "`"
                        + (ticket.category() == null ? "" : " [" + ticket.category() + "]"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (config().getBoolean("notify.joins", false)) {
            send(":green_circle: " + event.getPlayer().getName() + " joined the server.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (config().getBoolean("notify.quits", false)) {
            send(":red_circle: " + event.getPlayer().getName() + " left the server.");
        }
    }
}

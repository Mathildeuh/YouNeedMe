package fr.mathildeuh.youneedme.listener;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.api.model.PlayerProfile;
import fr.mathildeuh.youneedme.api.model.Position;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Cross-module player lifecycle wiring: everything that only makes sense once, server-wide, rather
 * than duplicated per feature - profile creation/persistence, ban/mute enforcement at the door,
 * vanish visibility, god-mode damage immunity, AFK tracking and the {@code /back} location trail.
 */
public final class PlayerLifecycleListener implements Listener {

    private final YouNeedMe plugin;

    public PlayerLifecycleListener(YouNeedMe plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        var moderation = plugin.services().moderation;
        var ban = moderation.activeBan(event.getUniqueId()).join();
        if (ban != null) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    plugin.lang()
                            .render(
                                    plugin.lang().defaultLocale(),
                                    "ban.screen_message",
                                    Placeholder.unparsed("player", event.getName()),
                                    Placeholder.unparsed(
                                            "date", java.time.Instant.now().toString()),
                                    Placeholder.unparsed("id", String.valueOf(ban.id())),
                                    Placeholder.unparsed("reason", ban.reason())));
            return;
        }
        String ip = event.getAddress().getHostAddress();
        var ipBan = moderation.activeIpBan(ip).join();
        if (ipBan != null) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    plugin.lang()
                            .render(
                                    plugin.lang().defaultLocale(),
                                    "banip.screen_message",
                                    Placeholder.unparsed("reason", ipBan.reason()),
                                    Placeholder.unparsed(
                                            "banner", String.valueOf(ipBan.issuedBy())),
                                    Placeholder.unparsed(
                                            "duration",
                                            ipBan.isPermanent()
                                                    ? "permanent"
                                                    : String.valueOf(ipBan.expiresAt()))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        plugin.services().joinedAt.put(player.getUniqueId(), now);
        plugin.services().lastActivity.put(player.getUniqueId(), now);

        for (var vanishedId : plugin.services().vanished) {
            if (!vanishedId.equals(player.getUniqueId())
                    && !player.hasPermission("youneedme.vanish.see")) {
                Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
                if (vanishedPlayer != null) {
                    player.hidePlayer(plugin, vanishedPlayer);
                }
            }
        }

        plugin.services()
                .storage
                .playerProfiles()
                .findOrCreate(player.getUniqueId(), player.getName())
                .thenAccept(
                        profile ->
                                plugin.scheduler().runGlobal(() -> applyProfile(player, profile)));
        // Warms HomeServiceImpl's name cache so /home and /delhome tab-completion has data as soon
        // as possible instead of only after the player's first /homes or /home use this session.
        plugin.services().homes.list(player.getUniqueId());
        // Without this, a player who never deposits/withdraws has no row in ynm_balances at all
        // (balance() only returns a virtual default, it never persists it) and is invisible to
        // /baltop even though /balance correctly shows their starting balance.
        plugin.services().economy.ensureAccountExists(player.getUniqueId());
    }

    private void applyProfile(Player player, PlayerProfile profile) {
        if (!player.isOnline()) {
            return;
        }
        plugin.services().nicknames.cache(player.getUniqueId(), profile.nickname());
        fr.mathildeuh.youneedme.modules.nickname.NicknameDisplay.apply(player, profile.nickname());
        if (profile.languageCode() != null) {
            plugin.lang().setPlayerLocaleOverride(player.getUniqueId(), profile.languageCode());
        }
        if (profile.lastLocation() != null) {
            var location = profile.lastLocation().toLocation();
            if (location != null) {
                plugin.services().lastLocation.put(player.getUniqueId(), location);
            }
        }
        if (profile.lastDeathLocation() != null) {
            var location = profile.lastDeathLocation().toLocation();
            if (location != null) {
                plugin.services().lastDeathLocation.put(player.getUniqueId(), location);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        java.util.UUID id = player.getUniqueId();
        long joinedAt = plugin.services().joinedAt.getOrDefault(id, System.currentTimeMillis());
        long sessionSeconds = Math.max(0, (System.currentTimeMillis() - joinedAt) / 1000);

        var back = plugin.services().lastLocation.get(id);
        var death = plugin.services().lastDeathLocation.get(id);
        plugin.services()
                .storage
                .playerProfiles()
                .find(id)
                .thenAccept(
                        opt ->
                                opt.ifPresent(
                                        profile -> {
                                            PlayerProfile updated =
                                                    new PlayerProfile(
                                                            profile.uuid(),
                                                            player.getName(),
                                                            profile.nickname(),
                                                            profile.languageCode(),
                                                            profile.firstJoinedAt(),
                                                            System.currentTimeMillis(),
                                                            profile.playtimeSeconds()
                                                                    + sessionSeconds,
                                                            back != null
                                                                    ? Position.of(back)
                                                                    : profile.lastLocation(),
                                                            death != null
                                                                    ? Position.of(death)
                                                                    : profile.lastDeathLocation());
                                            plugin.services()
                                                    .storage
                                                    .playerProfiles()
                                                    .save(updated);
                                        }));

        plugin.services().cooldowns.forgetPlayer(id);
        plugin.services().nicknames.forget(id);
        plugin.lang().forgetPlayer(id);
        plugin.services().afk.remove(id);
        plugin.services().godMode.remove(id);
        plugin.services().vanished.remove(id);
        plugin.services().tpa.forgetPlayer(id);
        plugin.services().joinedAt.remove(id);
        plugin.services().lastActivity.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom() != null) {
            plugin.services().lastLocation.put(event.getPlayer().getUniqueId(), event.getFrom());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.services().lastDeathLocation.put(player.getUniqueId(), player.getLocation());
        rewriteDeathMessageWithNicknames(event, player);
    }

    /**
     * The vanilla death message is built from the account's real username, not any display
     * name/nametag override - substitute the victim's (and, if applicable, the killer's) nickname
     * in place so a death doesn't leak a nicknamed player's real name in chat.
     */
    private void rewriteDeathMessageWithNicknames(PlayerDeathEvent event, Player victim) {
        Component message = event.deathMessage();
        if (message == null) {
            return;
        }
        message = replaceIfNicknamed(message, victim);
        Player killer = victim.getKiller();
        if (killer != null) {
            message = replaceIfNicknamed(message, killer);
        }
        event.deathMessage(message);
    }

    private Component replaceIfNicknamed(Component message, Player player) {
        return plugin.services()
                .nicknames
                .nickname(player.getUniqueId())
                .map(
                        nickname ->
                                message.replaceText(
                                        builder ->
                                                builder.matchLiteral(player.getName())
                                                        .replacement(
                                                                net.kyori.adventure.text.minimessage
                                                                        .MiniMessage.miniMessage()
                                                                        .deserialize(nickname))))
                .orElse(message);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.services().godMode.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var mute = plugin.services().moderation.activeMute(player.getUniqueId()).join();
        if (mute != null) {
            event.setCancelled(true);
            player.sendMessage(
                    plugin.lang()
                            .render(
                                    player,
                                    "mute.chat_blocked",
                                    Placeholder.unparsed("muter", String.valueOf(mute.issuedBy())),
                                    Placeholder.unparsed("reason", mute.reason()),
                                    Placeholder.unparsed(
                                            "expires",
                                            mute.isPermanent()
                                                    ? "never"
                                                    : String.valueOf(mute.expiresAt()))));
            return;
        }
        markActive(player);
        applyChatFormat(event, player);
    }

    private void applyChatFormat(AsyncChatEvent event, Player player) {
        var luckPerms = plugin.services().luckPerms;
        if (luckPerms == null
                || !plugin.configManager().module("chat").getBoolean("enabled", true)) {
            return;
        }
        String format =
                plugin.configManager().module("chat").getString("format", "{player}: {message}");
        String displayName =
                plugin.services().nicknames.nickname(player.getUniqueId()).orElse(player.getName());
        // LuckPerms prefixes/suffixes are conventionally legacy ("&6[VIP]") strings, not
        // MiniMessage - converted up front so the format template below can stay pure MiniMessage.
        Component prefix =
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(luckPerms.prefix(player.getUniqueId()));
        Component suffix =
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(luckPerms.suffix(player.getUniqueId()));
        event.renderer(
                ChatRenderer.viewerUnaware(
                        (source, sourceDisplayName, message) ->
                                MiniMessage.miniMessage()
                                        .deserialize(
                                                format,
                                                Placeholder.component("prefix", prefix),
                                                Placeholder.component("suffix", suffix),
                                                Placeholder.unparsed(
                                                        "group",
                                                        luckPerms.primaryGroup(
                                                                player.getUniqueId())),
                                                Placeholder.unparsed("player", displayName),
                                                Placeholder.component("message", message))));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var to = event.getTo();
        var from = event.getFrom();
        if (to != null
                && (from.getBlockX() != to.getBlockX()
                        || from.getBlockY() != to.getBlockY()
                        || from.getBlockZ() != to.getBlockZ())) {
            markActive(event.getPlayer());
        }
    }

    private void markActive(Player player) {
        plugin.services().lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (plugin.services().afk.remove(player.getUniqueId())) {
            player.sendMessage(plugin.lang().render(player, "afk.self.leave"));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    online.sendMessage(
                            plugin.lang()
                                    .render(
                                            online,
                                            "afk.broadcast.leave",
                                            Placeholder.unparsed("player", player.getName())));
                }
            }
        }
    }
}

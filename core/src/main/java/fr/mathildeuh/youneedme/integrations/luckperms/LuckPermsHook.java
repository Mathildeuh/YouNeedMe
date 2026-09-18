package fr.mathildeuh.youneedme.integrations.luckperms;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Caches each online player's LuckPerms prefix/suffix/primary group for cheap access from chat
 * formatting and placeholders, kept fresh not just at login but live via LuckPerms's own {@link
 * UserDataRecalculateEvent} (fired whenever permission/meta data changes, e.g. a group add/remove
 * from a web panel while the player is online) - a plain login-time snapshot would go stale the
 * moment staff changes someone's rank without a relog.
 */
public final class LuckPermsHook implements Listener {

    private record Display(String prefix, String suffix, String primaryGroup) {
        static final Display EMPTY = new Display("", "", "default");
    }

    private final LuckPerms api;
    private final Map<UUID, Display> cache = new ConcurrentHashMap<>();

    public LuckPermsHook(Plugin plugin) {
        this.api = LuckPermsProvider.get();
        api.getEventBus()
                .subscribe(
                        plugin, UserDataRecalculateEvent.class, event -> refresh(event.getUser()));
    }

    public void warm(UUID player) {
        User user = api.getUserManager().getUser(player);
        if (user != null) {
            refresh(user);
        }
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    public String prefix(UUID player) {
        return cache.getOrDefault(player, Display.EMPTY).prefix();
    }

    public String suffix(UUID player) {
        return cache.getOrDefault(player, Display.EMPTY).suffix();
    }

    public String primaryGroup(UUID player) {
        return cache.getOrDefault(player, Display.EMPTY).primaryGroup();
    }

    private void refresh(User user) {
        var meta = user.getCachedData().getMetaData();
        cache.put(
                user.getUniqueId(),
                new Display(
                        meta.getPrefix() == null ? "" : meta.getPrefix(),
                        meta.getSuffix() == null ? "" : meta.getSuffix(),
                        user.getPrimaryGroup()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        warm(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }
}

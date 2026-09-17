package fr.mathildeuh.youneedme.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory, per-player cooldown tracker keyed by an arbitrary string (kit id, warp name, "rtp", ...). */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    public void set(UUID player, String key, long durationMillis) {
        expiryByPlayer
                .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(key, System.currentTimeMillis() + durationMillis);
    }

    public long remainingMillis(UUID player, String key) {
        Long expiry = expiryByPlayer.getOrDefault(player, Map.of()).get(key);
        return expiry == null ? 0 : Math.max(0, expiry - System.currentTimeMillis());
    }

    public boolean isOnCooldown(UUID player, String key) {
        return remainingMillis(player, key) > 0;
    }

    public void clear(UUID player, String key) {
        Map<String, Long> playerCooldowns = expiryByPlayer.get(player);
        if (playerCooldowns != null) {
            playerCooldowns.remove(key);
        }
    }

    public void forgetPlayer(UUID player) {
        expiryByPlayer.remove(player);
    }
}

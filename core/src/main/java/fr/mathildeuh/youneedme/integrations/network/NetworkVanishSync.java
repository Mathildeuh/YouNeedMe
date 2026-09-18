package fr.mathildeuh.youneedme.integrations.network;

import fr.mathildeuh.youneedme.YouNeedMe;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * Syncs vanish state across every server in a network sharing the same Redis instance: a player
 * vanished on one server is fed straight into the local {@code Services#vanished} set that every
 * other vanish-aware check (PlaceholderAPI, {@code /user}, the join hide-loop) already reads, so
 * nothing else needs to know this class exists. Entirely optional and off by default - every method
 * here is a no-op unless {@code modules/network.yml}'s {@code enabled} is true and a Redis
 * connection was established.
 *
 * <p>Vanish visibility itself ({@code Player#hidePlayer}/{@code showPlayer}) is inherently
 * per-server - two players on different backend servers can never see each other regardless of
 * vanish state - so this only needs to keep the *set of vanished UUIDs* consistent everywhere, not
 * actually hide anyone across servers.
 */
public final class NetworkVanishSync {

    private static final String VANISH_SET_KEY = "youneedme:vanished";
    private static final String VANISH_CHANNEL = "youneedme:vanish";

    private final YouNeedMe plugin;
    private @Nullable JedisPool pool;
    private @Nullable JedisPubSub subscription;
    private volatile boolean enabled;

    private NetworkVanishSync(YouNeedMe plugin) {
        this.plugin = plugin;
    }

    public static NetworkVanishSync enable(YouNeedMe plugin) {
        NetworkVanishSync sync = new NetworkVanishSync(plugin);
        YamlConfiguration config = plugin.configManager().module("network");
        if (config.getBoolean("enabled", false)) {
            sync.start(config);
        }
        return sync;
    }

    private void start(YamlConfiguration config) {
        String host = config.getString("redis.host", "localhost");
        int port = config.getInt("redis.port", 6379);
        String password = config.getString("redis.password", "");
        try {
            this.pool =
                    password == null || password.isBlank()
                            ? new JedisPool(host, port)
                            : new JedisPool(host, port, null, password);
            seedExistingVanished();
            this.subscription =
                    new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            onNetworkMessage(message);
                        }
                    };
            this.enabled = true;
            Thread subscriberThread = new Thread(this::runSubscriber, "YouNeedMe-Redis-Vanish");
            subscriberThread.setDaemon(true);
            subscriberThread.start();
            plugin.getLogger()
                    .info("Cross-server vanish sync connected to Redis at " + host + ":" + port);
        } catch (RuntimeException e) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Could not connect to Redis for cross-server vanish sync - falling"
                                    + " back to local-only vanish.",
                            e);
            disable();
        }
    }

    private void seedExistingVanished() {
        try (Jedis jedis = pool.getResource()) {
            for (String raw : jedis.smembers(VANISH_SET_KEY)) {
                try {
                    plugin.services().vanished.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                    // Stale/corrupt entry from an incompatible publisher - skip rather than fail
                    // the whole sync over one bad row.
                }
            }
        }
    }

    private void runSubscriber() {
        try (Jedis jedis = pool.getResource()) {
            jedis.subscribe(subscription, VANISH_CHANNEL);
        } catch (RuntimeException e) {
            if (enabled) {
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Lost connection to Redis for cross-server vanish sync.",
                                e);
            }
        }
    }

    private void onNetworkMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        boolean vanished = Boolean.parseBoolean(parts[1]);
        plugin.scheduler().runGlobal(() -> applyRemoteState(uuid, vanished));
    }

    private void applyRemoteState(UUID uuid, boolean vanished) {
        if (vanished) {
            plugin.services().vanished.add(uuid);
        } else {
            plugin.services().vanished.remove(uuid);
        }
        Player subject = Bukkit.getPlayer(uuid);
        if (subject == null) {
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(uuid)) {
                continue;
            }
            if (vanished && !other.hasPermission("youneedme.vanish.see")) {
                other.hidePlayer(plugin, subject);
            } else if (!vanished) {
                other.showPlayer(plugin, subject);
            }
        }
    }

    /** Publishes a local vanish toggle to the network. A no-op if Redis isn't configured. */
    public void publish(UUID player, boolean vanished) {
        if (!enabled || pool == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            if (vanished) {
                jedis.sadd(VANISH_SET_KEY, player.toString());
            } else {
                jedis.srem(VANISH_SET_KEY, player.toString());
            }
            jedis.publish(VANISH_CHANNEL, player + ":" + vanished);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not publish vanish state to Redis.", e);
        }
    }

    /** Whether this instance is actually connected (vs. disabled/config-less). */
    public boolean isEnabled() {
        return enabled;
    }

    public void disable() {
        enabled = false;
        if (subscription != null && subscription.isSubscribed()) {
            subscription.unsubscribe();
        }
        if (pool != null) {
            pool.close();
        }
    }
}

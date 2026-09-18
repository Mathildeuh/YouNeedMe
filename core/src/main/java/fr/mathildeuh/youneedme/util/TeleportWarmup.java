package fr.mathildeuh.youneedme.util;

import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Shared "stand still for N seconds or the teleport is cancelled" countdown used by {@code /home},
 * {@code /back}, {@code /dback}, {@code /warp}, {@code /rtp} and {@code /spawn} - one listener
 * instead of five near-identical copies.
 */
public final class TeleportWarmup implements Listener {

    private record Pending(
            Location start, SchedulerAdapter.ScheduledTask task, Runnable onCancelled) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public boolean hasPending(UUID player) {
        return pending.containsKey(player);
    }

    public void cancel(UUID player) {
        Pending removed = pending.remove(player);
        if (removed != null) {
            removed.task().cancel();
        }
    }

    public void start(
            Player player,
            long seconds,
            SchedulerAdapter scheduler,
            Runnable onComplete,
            Runnable onCancelled) {
        UUID id = player.getUniqueId();
        if (seconds <= 0) {
            onComplete.run();
            return;
        }
        SchedulerAdapter.ScheduledTask task =
                scheduler.runGlobalDelayed(
                        () -> {
                            if (pending.remove(id) != null) {
                                onComplete.run();
                            }
                        },
                        seconds * 20L);
        pending.put(id, new Pending(player.getLocation(), task, onCancelled));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Pending pt = pending.get(event.getPlayer().getUniqueId());
        if (pt == null) {
            return;
        }
        Location from = pt.start();
        Location to = event.getTo();
        if (to == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        if (from.distanceSquared(to) > 0.09) {
            cancelWithCallback(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelWithCallback(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    private void cancelWithCallback(UUID id) {
        Pending removed = pending.remove(id);
        if (removed != null) {
            removed.task().cancel();
            removed.onCancelled().run();
        }
    }
}

package fr.mathildeuh.youneedme.api.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * A storage-friendly, always-serializable coordinate: unlike {@link Location} it never requires the
 * target {@link World} to be loaded to exist, so it is what every persisted model (homes, warps,
 * last-death location, ...) actually stores.
 */
public record Position(String worldName, double x, double y, double z, float yaw, float pitch) {

    public static Position of(Location location) {
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        return new Position(
                worldName,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    /**
     * Resolves this position back to a live {@link Location}, or {@code null} if its world isn't
     * loaded.
     */
    public @Nullable Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isWorldLoaded() {
        return Bukkit.getWorld(worldName) != null;
    }
}

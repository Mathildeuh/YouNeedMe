package fr.mathildeuh.youneedme.util;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Resolves {@code %placeholder%} tokens through PlaceholderAPI purely via reflection, so the build
 * never needs a compile-time dependency on it. No-ops (returns the text unchanged) when
 * PlaceholderAPI isn't installed.
 */
public final class PapiHook {

    private static final boolean PRESENT =
            Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    private static Method setPlaceholdersMethod;

    private PapiHook() {}

    public static boolean isPresent() {
        return PRESENT;
    }

    public static String apply(Player player, String text) {
        if (!PRESENT) {
            return text;
        }
        try {
            if (setPlaceholdersMethod == null) {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholdersMethod =
                        papiClass.getMethod(
                                "setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            }
            return (String) setPlaceholdersMethod.invoke(null, player, text);
        } catch (ReflectiveOperationException e) {
            return text;
        }
    }
}

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
    private static final Method SET_PLACEHOLDERS_METHOD = resolveSetPlaceholdersMethod();

    private PapiHook() {}

    private static Method resolveSetPlaceholdersMethod() {
        if (!PRESENT) {
            return null;
        }
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return papiClass.getMethod(
                    "setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static boolean isPresent() {
        return PRESENT;
    }

    public static String apply(Player player, String text) {
        if (!PRESENT || SET_PLACEHOLDERS_METHOD == null) {
            return text;
        }
        try {
            return (String) SET_PLACEHOLDERS_METHOD.invoke(null, player, text);
        } catch (ReflectiveOperationException e) {
            return text;
        }
    }
}

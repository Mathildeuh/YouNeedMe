package fr.mathildeuh.youneedme.integrations.luckperms;

import fr.mathildeuh.youneedme.YouNeedMe;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/** Wires the LuckPerms hook up if (and only if) LuckPerms is actually installed. */
public final class LuckPermsIntegration {

    private LuckPermsIntegration() {}

    public static @Nullable LuckPermsHook enable(YouNeedMe plugin) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger()
                    .info("LuckPerms not found - chat/placeholders fall back to plain usernames.");
            return null;
        }
        LuckPermsHook hook = new LuckPermsHook(plugin);
        Bukkit.getPluginManager().registerEvents(hook, plugin);
        for (var player : Bukkit.getOnlinePlayers()) {
            hook.warm(player.getUniqueId());
        }
        plugin.getLogger()
                .info("LuckPerms detected - chat/placeholders will show group prefixes/suffixes.");
        return hook;
    }
}

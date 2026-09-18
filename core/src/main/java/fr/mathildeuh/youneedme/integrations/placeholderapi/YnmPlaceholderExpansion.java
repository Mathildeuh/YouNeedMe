package fr.mathildeuh.youneedme.integrations.placeholderapi;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.util.TimeParser;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The official PlaceholderAPI expansion exposing YouNeedMe's own data (economy, homes, language,
 * nickname, AFK/vanish state, playtime, LuckPerms group when available) - see the brief's Section
 * 7. Registered automatically by {@link PlaceholderApiIntegration} when PlaceholderAPI is present.
 *
 * <p>Every placeholder here reads straight from the live service (blocking briefly on the async
 * call, same trade-off as the Vault provider) rather than a separate cache, so values are always
 * current at the cost of a small lookup per request - acceptable for a scoreboard/tab refresh rate,
 * not meant for a per-tick hot path.
 */
public final class YnmPlaceholderExpansion extends PlaceholderExpansion {

    private final YouNeedMe plugin;

    public YnmPlaceholderExpansion(YouNeedMe plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "youneedme";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        var services = plugin.services();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "balance" -> String.valueOf(services.economy.balance(player.getUniqueId()).join());
            case "balance_formatted" ->
                    services.economy
                            .currency("default")
                            .format(services.economy.balance(player.getUniqueId()).join());
            case "homes_count" ->
                    String.valueOf(services.homes.list(player.getUniqueId()).join().size());
            case "language" -> {
                Player online = player.getPlayer();
                if (online != null) {
                    yield plugin.lang().resolveLocale(online);
                }
                String override = plugin.lang().playerLocaleOverride(player.getUniqueId());
                yield override != null ? override : plugin.lang().defaultLocale();
            }
            case "nickname" ->
                    services.nicknames.nickname(player.getUniqueId()).orElse(player.getName());
            case "afk" -> String.valueOf(services.afk.contains(player.getUniqueId()));
            case "vanished" -> String.valueOf(services.vanished.contains(player.getUniqueId()));
            case "playtime" ->
                    services.storage
                            .playerProfiles()
                            .find(player.getUniqueId())
                            .join()
                            .map(profile -> TimeParser.format(profile.playtimeSeconds() * 1000))
                            .orElse("0s");
            case "group" ->
                    services.luckPerms == null
                            ? ""
                            : services.luckPerms.primaryGroup(player.getUniqueId());
            default -> null;
        };
    }
}

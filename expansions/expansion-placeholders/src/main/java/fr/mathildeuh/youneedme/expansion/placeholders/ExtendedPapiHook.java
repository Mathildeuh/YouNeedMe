package fr.mathildeuh.youneedme.expansion.placeholders;

import fr.mathildeuh.youneedme.api.YouNeedMeAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extra {@code %youneedme_extended_*%} placeholders, on top of the ones the core plugin already
 * registers - this is the part of the example expansion that's actually visible to a server admin,
 * reached purely through {@link YouNeedMeAPI} the same way any third-party plugin would.
 */
final class ExtendedPapiHook extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "youneedme_extended";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Mathildeuh";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return switch (params) {
            case "warps_count" ->
                    YouNeedMeAPI.warps()
                            .map(w -> w.list().join().size())
                            .map(String::valueOf)
                            .orElse("0");
            case "kits_total" ->
                    YouNeedMeAPI.kits().map(k -> k.kits().size()).map(String::valueOf).orElse("0");
            default -> null;
        };
    }
}

package fr.mathildeuh.youneedme.api.kits;

import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A kit definition. Kits are configuration, not player data (see {@code modules/kits.yml}), but
 * still modeled here so expansions can register additional kits programmatically (a "new kit
 * type", e.g. one generated from a template) without touching YAML.
 */
public record Kit(
        String id,
        String displayName,
        List<ItemStack> items,
        @Nullable String permission,
        long cooldownSeconds,
        boolean oneTime,
        @Nullable Integer maxClaims) {

    public boolean hasCooldown() {
        return cooldownSeconds > 0;
    }
}

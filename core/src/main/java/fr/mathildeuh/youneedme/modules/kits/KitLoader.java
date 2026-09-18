package fr.mathildeuh.youneedme.modules.kits;

import fr.mathildeuh.youneedme.api.kits.Kit;
import fr.mathildeuh.youneedme.util.ItemConfigCodec;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Parses {@code kits.yml} into immutable {@link Kit} definitions. */
public final class KitLoader {

    private KitLoader() {}

    public static List<Kit> load(YamlConfiguration config) {
        List<Kit> kits = new ArrayList<>();
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");
        if (kitsSection == null) {
            return kits;
        }
        for (String id : kitsSection.getKeys(false)) {
            ConfigurationSection section = kitsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            for (var raw : section.getMapList("items")) {
                ConfigurationSection itemSection =
                        new MemoryConfiguration().createSection("item", raw);
                ItemStack item = ItemConfigCodec.load(itemSection);
                if (item != null) {
                    items.add(item);
                }
            }
            int maxClaimsValue = section.getInt("max-claims", -1);
            kits.add(
                    new Kit(
                            id,
                            section.getString("display-name", id),
                            items,
                            section.contains("permission") ? section.getString("permission") : null,
                            section.getLong("cooldown-seconds", 0),
                            section.getBoolean("one-time", false),
                            maxClaimsValue < 0 ? null : maxClaimsValue));
        }
        return kits;
    }
}

package fr.mathildeuh.youneedme.modules.kits;

import fr.mathildeuh.youneedme.api.kits.Kit;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
                Object materialName = raw.get("material");
                if (materialName == null) {
                    continue;
                }
                Material material = Material.matchMaterial(materialName.toString());
                if (material == null) {
                    continue;
                }
                int amount = raw.get("amount") instanceof Number number ? number.intValue() : 1;
                items.add(new ItemStack(material, Math.max(1, amount)));
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

package fr.mathildeuh.youneedme.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Reads/writes the full set of item properties a config file (or an in-game item editor) can
 * express in human-readable YAML: material, amount, display name, lore, enchantments, item flags,
 * unbreakable, and custom model data - everything a kit or shop item plausibly needs, without
 * requiring hand-written NBT.
 */
public final class ItemConfigCodec {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ItemConfigCodec() {}

    /**
     * @return the item, or {@code null} if {@code section} has no (valid) {@code material}.
     */
    public static @Nullable ItemStack load(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (section.contains("display-name")) {
            meta.displayName(MINI_MESSAGE.deserialize(section.getString("display-name", "")));
        }
        if (section.contains("lore")) {
            meta.lore(
                    section.getStringList("lore").stream().map(MINI_MESSAGE::deserialize).toList());
        }
        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        meta.setUnbreakable(section.getBoolean("unbreakable", false));

        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                Enchantment enchantment = resolveEnchantment(key);
                if (enchantment != null) {
                    meta.addEnchant(enchantment, enchantments.getInt(key, 1), true);
                }
            }
        }

        for (String flagName : section.getStringList("flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                // Unknown flag name in config - skip it rather than fail the whole item.
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    public static void save(ConfigurationSection section, ItemStack item) {
        for (String key :
                List.of(
                        "material",
                        "amount",
                        "display-name",
                        "lore",
                        "enchantments",
                        "flags",
                        "custom-model-data",
                        "unbreakable")) {
            section.set(key, null);
        }
        toMap(item)
                .forEach(
                        (key, value) -> {
                            if ("enchantments".equals(key)
                                    && value instanceof Map<?, ?> enchantments) {
                                // set(path, Map) stores the raw Map rather than a nested
                                // ConfigurationSection, which getConfigurationSection() on the
                                // way back in would then fail to find.
                                ConfigurationSection enchantSection =
                                        section.createSection("enchantments");
                                enchantments.forEach(
                                        (k, v) -> enchantSection.set(String.valueOf(k), v));
                            } else {
                                section.set(key, value);
                            }
                        });
    }

    /**
     * Same data {@link #save} writes, as a plain (no {@link ConfigurationSection} involved) map -
     * what a config writer builds a YAML {@code items:} list out of, one of these per entry.
     */
    public static Map<String, Object> toMap(ItemStack item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material", item.getType().name());
        map.put("amount", item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return map;
        }
        Component displayName = meta.displayName();
        if (displayName != null) {
            map.put("display-name", MINI_MESSAGE.serialize(displayName));
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            map.put("lore", lore.stream().map(MINI_MESSAGE::serialize).toList());
        }
        if (meta.hasCustomModelData()) {
            map.put("custom-model-data", meta.getCustomModelData());
        }
        if (meta.isUnbreakable()) {
            map.put("unbreakable", true);
        }
        if (!meta.getEnchants().isEmpty()) {
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            meta.getEnchants()
                    .forEach(
                            (enchant, level) -> enchantments.put(enchant.getKey().getKey(), level));
            map.put("enchantments", enchantments);
        }
        if (!meta.getItemFlags().isEmpty()) {
            List<String> flags = new ArrayList<>();
            meta.getItemFlags().forEach(flag -> flags.add(flag.name()));
            map.put("flags", flags);
        }
        return map;
    }

    /** Plain-text (tag-stripped) summary of an item's display name, for chat/GUI listings. */
    public static String plainName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Component displayName = meta == null ? null : meta.displayName();
        if (displayName != null) {
            return PlainTextComponentSerializer.plainText().serialize(displayName);
        }
        return item.getType().name();
    }

    private static @Nullable Enchantment resolveEnchantment(String input) {
        NamespacedKey key =
                NamespacedKey.minecraft(input.toLowerCase(Locale.ROOT).replace(' ', '_'));
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }
}

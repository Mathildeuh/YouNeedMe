package fr.mathildeuh.youneedme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** MockBukkit is required: item/enchantment/registry resolution needs a live mock server. */
class ItemConfigCodecTest {

    @BeforeEach
    void mockServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void unmockServer() {
        MockBukkit.unmock();
    }

    @Test
    void loadsEveryPropertyOfAFullyConfiguredItem() {
        String yaml =
                """
                material: DIAMOND_SWORD
                amount: 1
                display-name: "<red>Excalibur"
                lore:
                  - "<gray>A legendary blade"
                enchantments:
                  sharpness: 5
                  unbreaking: 3
                flags: [HIDE_ENCHANTS, HIDE_ATTRIBUTES]
                unbreakable: true
                custom-model-data: 1001
                """;

        ItemStack item = ItemConfigCodec.load(section(yaml));

        assertNotNull(item);
        assertEquals(Material.DIAMOND_SWORD, item.getType());
        var meta = item.getItemMeta();
        assertEquals("Excalibur", ItemConfigCodec.plainName(item));
        assertEquals(1, meta.lore().size());
        assertEquals(5, meta.getEnchantLevel(Enchantment.SHARPNESS));
        assertEquals(3, meta.getEnchantLevel(Enchantment.UNBREAKING));
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));
        assertTrue(meta.isUnbreakable());
        assertEquals(1001f, meta.getCustomModelDataComponent().getFloats().get(0));
    }

    @Test
    void aMissingMaterialYieldsNull() {
        assertNull(ItemConfigCodec.load(section("amount: 1")));
    }

    @Test
    void aMinimalItemHasNoExtraMetadata() {
        ItemStack item = ItemConfigCodec.load(section("material: STONE"));
        var meta = item.getItemMeta();
        assertFalse(meta.hasDisplayName());
        assertFalse(meta.hasLore());
        assertFalse(meta.isUnbreakable());
        assertTrue(meta.getEnchants().isEmpty());
    }

    @Test
    void saveRoundTripsBackIntoTheSameShape() {
        ItemStack original =
                ItemConfigCodec.load(
                        section(
                                """
                                material: BOW
                                enchantments:
                                  power: 4
                                flags: [HIDE_ENCHANTS]
                                unbreakable: true
                                """));

        var target = new YamlConfiguration();
        ItemConfigCodec.save(target.createSection("item"), original);
        ItemStack reloaded = ItemConfigCodec.load(target.getConfigurationSection("item"));

        assertEquals(original.getType(), reloaded.getType());
        assertEquals(
                original.getItemMeta().getEnchantLevel(Enchantment.POWER),
                reloaded.getItemMeta().getEnchantLevel(Enchantment.POWER));
        assertTrue(reloaded.getItemMeta().isUnbreakable());
    }

    private static org.bukkit.configuration.ConfigurationSection section(String yaml) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        return config.createSection("wrapper", config.getValues(false));
    }
}

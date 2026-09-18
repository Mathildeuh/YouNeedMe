package fr.mathildeuh.youneedme.modules.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.mathildeuh.youneedme.api.kits.Kit;
import java.io.StringReader;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * {@code MockBukkit.mock()} is required even though nothing here touches a mock server directly:
 * constructing an {@link org.bukkit.inventory.ItemStack} resolves the material through Paper's
 * registry system, which needs a live {@code Bukkit.getServer()}.
 */
class KitLoaderTest {

    @BeforeEach
    void mockServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void unmockServer() {
        MockBukkit.unmock();
    }

    @Test
    void parsesEveryFieldOfAFullyConfiguredKit() {
        String yaml =
                """
                kits:
                  vip:
                    display-name: "VIP Kit"
                    permission: youneedme.kit.vip
                    cooldown-seconds: 43200
                    one-time: false
                    max-claims: 100
                    items:
                      - { material: DIAMOND_SWORD, amount: 1 }
                      - { material: DIAMOND, amount: 8 }
                """;

        List<Kit> kits = KitLoader.load(load(yaml));

        assertEquals(1, kits.size());
        Kit vip = kits.get(0);
        assertEquals("vip", vip.id());
        assertEquals("VIP Kit", vip.displayName());
        assertEquals("youneedme.kit.vip", vip.permission());
        assertEquals(43_200L, vip.cooldownSeconds());
        assertTrue(vip.hasCooldown());
        assertFalse(vip.oneTime());
        assertEquals(100, vip.maxClaims());
        assertEquals(2, vip.items().size());
        assertEquals(Material.DIAMOND_SWORD, vip.items().get(0).getType());
        assertEquals(8, vip.items().get(1).getAmount());
    }

    @Test
    void minimalKitFallsBackToSensibleDefaults() {
        String yaml =
                """
                kits:
                  starter:
                    items:
                      - { material: BREAD, amount: 16 }
                """;

        Kit starter = KitLoader.load(load(yaml)).get(0);

        assertEquals("starter", starter.id());
        assertEquals("starter", starter.displayName());
        assertNull(starter.permission());
        assertFalse(starter.hasCooldown());
        assertFalse(starter.oneTime());
        assertNull(starter.maxClaims());
    }

    @Test
    void anItemWithNoAmountDefaultsToOne() {
        String yaml =
                """
                kits:
                  simple:
                    items:
                      - { material: TORCH }
                """;

        Kit kit = KitLoader.load(load(yaml)).get(0);
        assertEquals(1, kit.items().get(0).getAmount());
    }

    @Test
    void anItemWithAnUnknownMaterialIsSkippedRatherThanCrashing() {
        String yaml =
                """
                kits:
                  broken:
                    items:
                      - { material: NOT_A_REAL_MATERIAL }
                      - { material: STONE }
                """;

        Kit kit = KitLoader.load(load(yaml)).get(0);
        assertEquals(1, kit.items().size());
        assertEquals(Material.STONE, kit.items().get(0).getType());
    }

    @Test
    void aMissingKitsSectionYieldsAnEmptyList() {
        assertTrue(KitLoader.load(load("other: value")).isEmpty());
    }

    private static YamlConfiguration load(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }
}

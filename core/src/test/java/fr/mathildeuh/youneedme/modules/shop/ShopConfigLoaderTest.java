package fr.mathildeuh.youneedme.modules.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.mathildeuh.youneedme.api.shop.ShopCategory;
import fr.mathildeuh.youneedme.api.shop.ShopItem;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
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
class ShopConfigLoaderTest {

    @BeforeEach
    void mockServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void unmockServer() {
        MockBukkit.unmock();
    }

    @Test
    void parsesCategoriesOrderedByDisplayOrder() {
        String yaml =
                """
                categories:
                  ores:
                    display-name: "Ores"
                    icon: IRON_ORE
                    order: 1
                    items:
                      iron:
                        material: IRON_INGOT
                        buy-price: 25.0
                        sell-price: 12.0
                  blocks:
                    display-name: "Blocks"
                    icon: STONE
                    order: 0
                    items:
                      stone:
                        material: STONE
                        sell-price: 0.5
                """;

        List<ShopCategory> categories = ShopConfigLoader.load(load(yaml));

        assertEquals(2, categories.size());
        assertEquals("blocks", categories.get(0).id());
        assertEquals("ores", categories.get(1).id());
    }

    @Test
    void anItemWithNoStockKeyIsUnlimited() {
        String yaml =
                """
                categories:
                  ores:
                    items:
                      iron:
                        material: IRON_INGOT
                        buy-price: 25.0
                """;

        ShopItem item = firstItem(yaml);
        assertNull(item.stock());
        assertEquals(Material.IRON_INGOT, item.display().getType());
        assertEquals(25.0, item.buyPrice());
        assertNull(item.sellPrice());
    }

    @Test
    void anExplicitStockValueIsRespected() {
        String yaml =
                """
                categories:
                  blackmarket:
                    items:
                      netherite:
                        material: NETHERITE_INGOT
                        buy-price: 5000.0
                        stock: 10
                """;

        ShopItem item = firstItem(yaml);
        assertEquals(10, item.stock());
    }

    @Test
    void anItemWithAnUnknownMaterialIsSkippedRatherThanCrashing() {
        String yaml =
                """
                categories:
                  broken:
                    items:
                      bad:
                        material: NOT_A_REAL_MATERIAL
                """;

        List<ShopCategory> categories = ShopConfigLoader.load(load(yaml));
        assertTrue(categories.get(0).items().isEmpty());
    }

    @Test
    void aCategoryWithNoPermissionKeyHasNoRestriction() {
        String yaml =
                """
                categories:
                  open:
                    items: {}
                """;

        List<ShopCategory> categories = ShopConfigLoader.load(load(yaml));
        assertEquals(Optional.empty(), Optional.ofNullable(categories.get(0).permission()));
    }

    private static ShopItem firstItem(String yaml) {
        List<ShopCategory> categories = ShopConfigLoader.load(load(yaml));
        return categories.get(0).items().get(0);
    }

    private static YamlConfiguration load(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }
}

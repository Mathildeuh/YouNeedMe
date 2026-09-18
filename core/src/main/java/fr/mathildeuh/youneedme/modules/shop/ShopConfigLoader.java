package fr.mathildeuh.youneedme.modules.shop;

import fr.mathildeuh.youneedme.api.shop.ShopCategory;
import fr.mathildeuh.youneedme.api.shop.ShopItem;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Parses {@code shop.yml} into the immutable {@link ShopCategory}/{@link ShopItem} model. */
public final class ShopConfigLoader {

    private ShopConfigLoader() {}

    public static List<ShopCategory> load(YamlConfiguration config) {
        List<ShopCategory> categories = new ArrayList<>();
        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection == null) {
            return categories;
        }
        for (String categoryId : categoriesSection.getKeys(false)) {
            ConfigurationSection categorySection =
                    categoriesSection.getConfigurationSection(categoryId);
            if (categorySection == null) {
                continue;
            }
            Material icon = Material.matchMaterial(categorySection.getString("icon", "CHEST"));
            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemsSection = categorySection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemId : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                    if (itemSection == null) {
                        continue;
                    }
                    Material material =
                            Material.matchMaterial(itemSection.getString("material", itemId));
                    if (material == null) {
                        continue;
                    }
                    Double buyPrice =
                            itemSection.contains("buy-price")
                                    ? itemSection.getDouble("buy-price")
                                    : null;
                    Double sellPrice =
                            itemSection.contains("sell-price")
                                    ? itemSection.getDouble("sell-price")
                                    : null;
                    int stockValue = itemSection.getInt("stock", -1);
                    Integer stock = stockValue < 0 ? null : stockValue;
                    items.add(
                            new ShopItem(
                                    itemId, new ItemStack(material), buyPrice, sellPrice, stock));
                }
            }
            categories.add(
                    new ShopCategory(
                            categoryId,
                            categorySection.getString("display-name", categoryId),
                            icon == null ? Material.CHEST : icon,
                            categorySection.getInt("order", 0),
                            categorySection.contains("permission")
                                    ? categorySection.getString("permission")
                                    : null,
                            items));
        }
        categories.sort(java.util.Comparator.comparingInt(ShopCategory::displayOrder));
        return categories;
    }
}

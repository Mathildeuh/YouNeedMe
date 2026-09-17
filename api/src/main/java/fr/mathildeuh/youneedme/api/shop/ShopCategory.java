package fr.mathildeuh.youneedme.api.shop;

import java.util.List;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

public record ShopCategory(
        String id,
        String displayName,
        Material icon,
        int displayOrder,
        @Nullable String permission,
        List<ShopItem> items) {}

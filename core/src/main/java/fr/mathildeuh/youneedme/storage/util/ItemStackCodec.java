package fr.mathildeuh.youneedme.storage.util;

import java.util.Base64;
import org.bukkit.inventory.ItemStack;

/**
 * Base64 (de)serialization for the one place an {@link ItemStack} needs to be persisted as text:
 * auction listings.
 */
public final class ItemStackCodec {

    private ItemStackCodec() {}

    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String base64) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
    }
}

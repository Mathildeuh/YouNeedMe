package fr.mathildeuh.youneedme.lang;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Loads {@code lang/<locale>.json} (MiniMessage-formatted, {@code <placeholder>}-style templates)
 * and renders them per player: a player with no stored language preference gets their client's own
 * locale if a matching file exists, otherwise the server's configured default, and any key missing
 * from a non-default locale silently falls back to the default before finally rendering {@code
 * error.missing_key}.
 */
public final class LanguageManager {

    private static final Gson GSON = new Gson();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final TagResolver[] NO_PLACEHOLDERS = new TagResolver[0];

    private final Logger logger;
    private final Map<String, Map<String, String>> messages = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerLocaleOverrides = new ConcurrentHashMap<>();
    private String defaultLocale;

    public LanguageManager(Logger logger) {
        this.logger = logger;
    }

    public void load(Path langResourceDir, String defaultLocale) {
        this.defaultLocale = defaultLocale;
        messages.clear();
        try (var files = Files.list(langResourceDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(this::loadFile);
        } catch (IOException e) {
            logger.severe(
                    "Could not list language files in " + langResourceDir + ": " + e.getMessage());
        }
        if (!messages.containsKey(defaultLocale)) {
            logger.warning(
                    "Default locale '"
                            + defaultLocale
                            + "' has no matching lang file - falling back to en_US.");
            this.defaultLocale = "en_US";
        }
        logger.info(
                "Loaded " + messages.size() + " languages (default: " + this.defaultLocale + ").");
    }

    private void loadFile(Path path) {
        String locale = path.getFileName().toString().replace(".json", "");
        try (InputStream in = Files.newInputStream(path);
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Type type = TypeToken.getParameterized(Map.class, String.class, String.class).getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            messages.put(locale, loaded == null ? Map.of() : loaded);
        } catch (IOException e) {
            logger.warning("Failed to load language file " + path + ": " + e.getMessage());
        }
    }

    public Set<String> availableLocales() {
        return messages.keySet();
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    public boolean hasLocale(String code) {
        return messages.containsKey(code);
    }

    /**
     * Resolves a player-typed locale code to a loaded one: exact match first (e.g. {@code fr_FR}),
     * then a case-insensitive match, then the first loaded locale whose language part matches (so
     * {@code /language fr} works without requiring the exact {@code fr_FR} file name).
     */
    @Nullable
    public String matchLocale(String input) {
        if (hasLocale(input)) {
            return input;
        }
        for (String code : messages.keySet()) {
            if (code.equalsIgnoreCase(input)) {
                return code;
            }
        }
        String prefix = input.toLowerCase(Locale.ROOT) + "_";
        for (String code : messages.keySet()) {
            if (code.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return code;
            }
        }
        return null;
    }

    public void setPlayerLocaleOverride(UUID player, @Nullable String localeCode) {
        if (localeCode == null) {
            playerLocaleOverrides.remove(player);
        } else {
            playerLocaleOverrides.put(player, localeCode);
        }
    }

    public void forgetPlayer(UUID player) {
        playerLocaleOverrides.remove(player);
    }

    @Nullable
    public String playerLocaleOverride(UUID player) {
        return playerLocaleOverrides.get(player);
    }

    /**
     * The effective locale for a sender: explicit override, else their client locale if we have it,
     * else the default.
     */
    public String resolveLocale(CommandSender sender) {
        if (sender instanceof Player player) {
            String override = playerLocaleOverrides.get(player.getUniqueId());
            if (override != null) {
                return hasLocale(override) ? override : defaultLocale;
            }
            String clientLocale = clientLocaleKey(player);
            if (hasLocale(clientLocale)) {
                return clientLocale;
            }
        }
        return defaultLocale;
    }

    private String clientLocaleKey(Player player) {
        Locale locale = player.locale();
        return locale.getLanguage() + "_" + locale.getCountry().toUpperCase(Locale.ROOT);
    }

    public Component render(CommandSender sender, String key, TagResolver... placeholders) {
        return render(resolveLocale(sender), key, placeholders);
    }

    public Component render(String localeCode, String key, TagResolver... placeholders) {
        String template = lookup(localeCode, key);
        if (template == null) {
            // Both the requested key and (if we get here recursively) "error.missing_key" itself
            // are absent from every loaded locale - a corrupted install. Never recurse forever.
            if ("error.missing_key".equals(key)) {
                return Component.text("[YouNeedMe] Missing language key and no fallback available");
            }
            String fallbackLocale = messages.containsKey(defaultLocale) ? defaultLocale : "en_US";
            return render(fallbackLocale, "error.missing_key", Placeholder.unparsed("key", key));
        }
        try {
            return MINI_MESSAGE.deserialize(template, placeholders);
        } catch (RuntimeException e) {
            logger.warning(
                    "Malformed MiniMessage template for key '"
                            + key
                            + "' ("
                            + localeCode
                            + "): "
                            + e.getMessage());
            return Component.text(template);
        }
    }

    /**
     * Plain-text render (no MiniMessage parsing), for places that need a raw string - e.g. a
     * webhook payload.
     */
    public String renderPlain(String localeCode, String key, TagResolver... placeholders) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(render(localeCode, key, placeholders));
    }

    public boolean hasKey(String localeCode, String key) {
        return lookup(localeCode, key) != null;
    }

    private @Nullable String lookup(String localeCode, String key) {
        String direct = messages.getOrDefault(localeCode, Map.of()).get(key);
        if (direct != null) {
            return direct;
        }
        if (!localeCode.equals(defaultLocale)) {
            return messages.getOrDefault(defaultLocale, Map.of()).get(key);
        }
        return null;
    }

    public static TagResolver[] noPlaceholders() {
        return NO_PLACEHOLDERS;
    }

    public static List<String> sortedLocales(Set<String> locales) {
        return locales.stream().sorted().toList();
    }
}

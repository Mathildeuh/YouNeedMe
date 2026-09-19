package fr.mathildeuh.youneedme.integrations.update;

import com.google.gson.JsonParser;
import fr.mathildeuh.youneedme.YouNeedMe;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Checks the GitHub Releases API once on startup for a newer tag than the running version, and (if
 * one exists) logs it to console plus nags an operator on join - both entirely best-effort: any
 * network failure, rate limit, or malformed response is swallowed silently, never delays startup
 * (the whole check runs async), and never blocks or affects plugin functionality either way.
 *
 * <p>Disabled entirely via {@code config.yml}'s {@code update-checker: false}.
 */
public final class UpdateChecker implements Listener {

    private static final String RELEASES_URL =
            "https://api.github.com/repos/Mathildeuh/YouNeedMe/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final YouNeedMe plugin;
    private volatile String latestVersion;

    private UpdateChecker(YouNeedMe plugin) {
        this.plugin = plugin;
    }

    public static void check(YouNeedMe plugin) {
        if (!plugin.configManager().mainConfig().getBoolean("update-checker", true)) {
            return;
        }
        UpdateChecker checker = new UpdateChecker(plugin);
        Bukkit.getPluginManager().registerEvents(checker, plugin);
        plugin.scheduler().runAsync(checker::fetchLatestVersion);
    }

    private void fetchLatestVersion() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(RELEASES_URL))
                            .timeout(TIMEOUT)
                            .header("Accept", "application/vnd.github+json")
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }
            String tag =
                    JsonParser.parseString(response.body())
                            .getAsJsonObject()
                            .get("tag_name")
                            .getAsString();
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            String current = plugin.getPluginMeta().getVersion();
            if (isNewer(version, current)) {
                this.latestVersion = version;
                plugin.getLogger()
                        .info(
                                "A new version of YouNeedMe is available: "
                                        + version
                                        + " (running "
                                        + current
                                        + "). Get it from"
                                        + " https://github.com/Mathildeuh/YouNeedMe/releases/latest");
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            plugin.getLogger()
                    .log(Level.FINE, "Update check failed (non-fatal): " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Simple numeric x.y.z comparison - both versions here always come from release-please tags.
     */
    private static boolean isNewer(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int latestPart = part(latestParts, i);
            int currentPart = part(currentParts, i);
            if (latestPart != currentPart) {
                return latestPart > currentPart;
            }
        }
        return false;
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String version = latestVersion;
        CommandSender player = event.getPlayer();
        if (version != null && player.hasPermission("youneedme.admin")) {
            player.sendMessage(
                    Component.text(
                            "[YouNeedMe] Update available: "
                                    + version
                                    + " (running "
                                    + plugin.getPluginMeta().getVersion()
                                    + ")",
                            NamedTextColor.YELLOW));
        }
    }
}

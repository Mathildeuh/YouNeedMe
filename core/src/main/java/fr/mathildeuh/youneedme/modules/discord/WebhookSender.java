package fr.mathildeuh.youneedme.modules.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Posts plain Discord webhook messages - zero dependency, works even without DiscordSRV installed.
 */
public final class WebhookSender {

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String webhookUrl;
    private final Logger logger;

    public WebhookSender(String webhookUrl, Logger logger) {
        this.webhookUrl = webhookUrl;
        this.logger = logger;
    }

    public boolean isConfigured() {
        return webhookUrl != null && webhookUrl.startsWith("http");
    }

    public CompletableFuture<Void> send(String content) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }
        String payload = "{\"content\":\"" + escape(content) + "\"}";
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(
                        response -> {
                            if (response.statusCode() >= 300) {
                                logger.warning(
                                        "Discord webhook responded with HTTP "
                                                + response.statusCode());
                            }
                        })
                .exceptionally(
                        t -> {
                            logger.warning("Failed to send Discord webhook: " + t.getMessage());
                            return null;
                        });
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

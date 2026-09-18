package fr.mathildeuh.youneedme.integrations.floodgate;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;

/** Detects Bedrock (Geyser/Floodgate) players so features can degrade gracefully for them. */
public final class FloodgateHook {

    private FloodgateHook() {}

    public static boolean isBedrockPlayer(UUID player) {
        return FloodgateApi.getInstance().isFloodgatePlayer(player);
    }
}

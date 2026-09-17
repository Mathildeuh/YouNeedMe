package fr.mathildeuh.youneedme.api.model;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Cross-module, per-player data that doesn't belong to any single feature: identity, nickname,
 * language choice, playtime and the last few locations used by {@code /back} and {@code /dback}.
 */
public record PlayerProfile(
        UUID uuid,
        String lastKnownUsername,
        @Nullable String nickname,
        @Nullable String languageCode,
        long firstJoinedAt,
        long lastSeenAt,
        long playtimeSeconds,
        @Nullable Position lastLocation,
        @Nullable Position lastDeathLocation) {

    public PlayerProfile withNickname(@Nullable String newNickname) {
        return new PlayerProfile(
                uuid,
                lastKnownUsername,
                newNickname,
                languageCode,
                firstJoinedAt,
                lastSeenAt,
                playtimeSeconds,
                lastLocation,
                lastDeathLocation);
    }

    public PlayerProfile withLanguageCode(@Nullable String newLanguageCode) {
        return new PlayerProfile(
                uuid,
                lastKnownUsername,
                nickname,
                newLanguageCode,
                firstJoinedAt,
                lastSeenAt,
                playtimeSeconds,
                lastLocation,
                lastDeathLocation);
    }

    public PlayerProfile withLastLocation(@Nullable Position newLastLocation) {
        return new PlayerProfile(
                uuid,
                lastKnownUsername,
                nickname,
                languageCode,
                firstJoinedAt,
                lastSeenAt,
                playtimeSeconds,
                newLastLocation,
                lastDeathLocation);
    }

    public PlayerProfile withLastDeathLocation(@Nullable Position newLastDeathLocation) {
        return new PlayerProfile(
                uuid,
                lastKnownUsername,
                nickname,
                languageCode,
                firstJoinedAt,
                lastSeenAt,
                playtimeSeconds,
                lastLocation,
                newLastDeathLocation);
    }

    public String displayName() {
        return nickname != null ? nickname : lastKnownUsername;
    }
}

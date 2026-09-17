package fr.mathildeuh.youneedme.api.nickname;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/** Player nicknames, with real-name resolution for staff ({@code /realname}). */
public interface NicknameService {

    Optional<String> nickname(UUID player);

    /**
     * @param nickname the MiniMessage-formatted nickname to set, or {@code null} to clear it
     * @return {@code false} if {@code nickname} fails the configured validation (blacklist, length)
     */
    CompletableFuture<Boolean> setNickname(UUID player, @Nullable String nickname);

    /** Resolves a nickname (or raw username) back to the account it belongs to. */
    Optional<UUID> resolveRealPlayer(String nicknameOrUsername);
}

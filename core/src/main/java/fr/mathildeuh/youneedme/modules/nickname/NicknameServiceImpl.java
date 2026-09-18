package fr.mathildeuh.youneedme.modules.nickname;

import fr.mathildeuh.youneedme.api.nickname.NicknameService;
import fr.mathildeuh.youneedme.api.storage.PlayerProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public final class NicknameServiceImpl implements NicknameService {

    private final PlayerProfileRepository repository;
    private final int maxLength;
    private final List<String> blacklist;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public NicknameServiceImpl(
            PlayerProfileRepository repository, int maxLength, List<String> blacklist) {
        this.repository = repository;
        this.maxLength = maxLength;
        this.blacklist = blacklist;
    }

    @Override
    public Optional<String> nickname(UUID player) {
        return Optional.ofNullable(cache.get(player));
    }

    @Override
    public CompletableFuture<Boolean> setNickname(UUID player, @Nullable String nickname) {
        if (nickname != null) {
            String plain = Pattern.compile("<[^>]*>").matcher(nickname).replaceAll("");
            if (plain.length() > maxLength) {
                return CompletableFuture.completedFuture(false);
            }
            String lower = plain.toLowerCase(java.util.Locale.ROOT);
            for (String word : blacklist) {
                if (!word.isBlank() && lower.contains(word.toLowerCase(java.util.Locale.ROOT))) {
                    return CompletableFuture.completedFuture(false);
                }
            }
        }
        return repository
                .find(player)
                .thenCompose(
                        opt -> {
                            if (opt.isEmpty()) {
                                return CompletableFuture.completedFuture(false);
                            }
                            return repository
                                    .save(opt.get().withNickname(nickname))
                                    .thenApply(
                                            v -> {
                                                if (nickname == null) {
                                                    cache.remove(player);
                                                } else {
                                                    cache.put(player, nickname);
                                                }
                                                return true;
                                            });
                        });
    }

    @Override
    public Optional<UUID> resolveRealPlayer(String nicknameOrUsername) {
        for (Map.Entry<UUID, String> entry : cache.entrySet()) {
            if (stripTags(entry.getValue()).equalsIgnoreCase(nicknameOrUsername)) {
                return Optional.of(entry.getKey());
            }
        }
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(nicknameOrUsername);
        return online != null ? Optional.of(online.getUniqueId()) : Optional.empty();
    }

    public void cache(UUID player, @Nullable String nickname) {
        if (nickname == null) {
            cache.remove(player);
        } else {
            cache.put(player, nickname);
        }
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    private static String stripTags(String value) {
        return Pattern.compile("<[^>]*>").matcher(value).replaceAll("");
    }
}

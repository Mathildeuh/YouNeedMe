package fr.mathildeuh.youneedme.modules.homes;

import fr.mathildeuh.youneedme.api.event.HomeSetEvent;
import fr.mathildeuh.youneedme.api.homes.HomeService;
import fr.mathildeuh.youneedme.api.model.Home;
import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.storage.HomeRepository;
import fr.mathildeuh.youneedme.util.SyncEvents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class HomeServiceImpl implements HomeService {

    private final HomeRepository repository;
    private final SchedulerAdapter scheduler;
    private final int defaultLimit;
    // Tab-completion needs home names synchronously, but the repository is async (DB-backed) -
    // getNow(default) on a future that's never actually done in time would always return empty.
    // This cache is refreshed every time list() resolves and on every set()/delete(), so it lags
    // by at most one round-trip instead of never having data at all.
    private final Map<UUID, List<String>> nameCache = new ConcurrentHashMap<>();

    public HomeServiceImpl(
            HomeRepository repository, SchedulerAdapter scheduler, int defaultLimit) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.defaultLimit = defaultLimit;
    }

    @Override
    public CompletableFuture<List<Home>> list(UUID owner) {
        return repository
                .findByOwner(owner)
                .thenApply(
                        homes -> {
                            nameCache.put(owner, homes.stream().map(Home::name).toList());
                            return homes;
                        });
    }

    /** Synchronous, possibly-stale home names for {@code owner} - tab-completion only. */
    public List<String> cachedNames(UUID owner) {
        return nameCache.getOrDefault(owner, List.of());
    }

    @Override
    public CompletableFuture<Home> get(UUID owner, String name) {
        return repository.find(owner, name).thenApply(opt -> opt.orElse(null));
    }

    @Override
    public int limitFor(UUID owner) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            return defaultLimit;
        }
        int highest = defaultLimit;
        for (org.bukkit.permissions.PermissionAttachmentInfo info :
                player.getEffectivePermissions()) {
            String permission = info.getPermission();
            if (info.getValue() && permission.startsWith("youneedme.homes.limit.")) {
                try {
                    int value =
                            Integer.parseInt(
                                    permission.substring("youneedme.homes.limit.".length()));
                    highest = Math.max(highest, value);
                } catch (NumberFormatException e) {
                    // ignore: not a numeric limit node
                }
            }
            if (info.getValue() && "youneedme.homes.limit.unlimited".equals(permission)) {
                return Integer.MAX_VALUE;
            }
        }
        return highest;
    }

    @Override
    public CompletableFuture<Home> set(UUID owner, String name, Position position) {
        return repository
                .find(owner, name)
                .thenCompose(
                        existing -> {
                            if (existing.isPresent()) {
                                return doSet(
                                        owner, name, position, true, existing.get().createdAt());
                            }
                            int limit = limitFor(owner);
                            return repository
                                    .count(owner)
                                    .thenCompose(
                                            count -> {
                                                if (count >= limit) {
                                                    CompletableFuture<Home> failed =
                                                            new CompletableFuture<>();
                                                    failed.completeExceptionally(
                                                            new HomeLimitExceededException(limit));
                                                    return failed;
                                                }
                                                return doSet(
                                                        owner,
                                                        name,
                                                        position,
                                                        false,
                                                        System.currentTimeMillis());
                                            });
                        });
    }

    private CompletableFuture<Home> doSet(
            UUID owner, String name, Position position, boolean overwrite, long createdAt) {
        Player player = Bukkit.getPlayer(owner);
        CompletableFuture<HomeSetEvent> eventFuture;
        if (player != null) {
            eventFuture =
                    SyncEvents.fire(scheduler, new HomeSetEvent(player, name, position, overwrite));
        } else {
            eventFuture = CompletableFuture.completedFuture(null);
        }
        return eventFuture.thenCompose(
                event -> {
                    if (event != null && event.isCancelled()) {
                        CompletableFuture<Home> cancelled = new CompletableFuture<>();
                        cancelled.completeExceptionally(
                                new IllegalStateException("Cancelled by another plugin"));
                        return cancelled;
                    }
                    Home home =
                            new Home(owner, name, position, createdAt, System.currentTimeMillis());
                    return repository
                            .save(home)
                            .thenApply(
                                    v -> {
                                        list(owner);
                                        return home;
                                    });
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID owner, String name) {
        return repository
                .delete(owner, name)
                .thenApply(
                        deleted -> {
                            if (deleted) {
                                list(owner);
                            }
                            return deleted;
                        });
    }
}

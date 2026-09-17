package fr.mathildeuh.youneedme.modules.warps;

import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.model.Warp;
import fr.mathildeuh.youneedme.api.storage.WarpRepository;
import fr.mathildeuh.youneedme.api.warps.WarpService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

public final class WarpServiceImpl implements WarpService {

    private final WarpRepository repository;
    private final ConcurrentHashMap<String, Warp> cache = new ConcurrentHashMap<>();

    public WarpServiceImpl(WarpRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Void> warmCache() {
        return repository
                .findAll()
                .thenAccept(
                        warps -> {
                            cache.clear();
                            warps.forEach(
                                    w -> cache.put(w.name().toLowerCase(java.util.Locale.ROOT), w));
                        });
    }

    @Override
    public CompletableFuture<List<Warp>> list() {
        return CompletableFuture.completedFuture(List.copyOf(cache.values()));
    }

    @Override
    public CompletableFuture<List<Warp>> listVisibleTo(UUID viewer) {
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(viewer);
        return list().thenApply(
                        warps ->
                                warps.stream()
                                        .filter(
                                                w ->
                                                        !w.hidden()
                                                                || (player != null
                                                                        && player.hasPermission(
                                                                                "youneedme.warpadmin")))
                                        .filter(
                                                w ->
                                                        w.permission() == null
                                                                || player == null
                                                                || player.hasPermission(
                                                                        w.permission()))
                                        .toList());
    }

    @Override
    public CompletableFuture<@Nullable Warp> get(String name) {
        return CompletableFuture.completedFuture(
                cache.get(name.toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public CompletableFuture<Warp> create(
            String name, Position position, @Nullable UUID createdBy) {
        Warp warp =
                new Warp(
                        name,
                        position,
                        null,
                        null,
                        0,
                        null,
                        false,
                        createdBy,
                        System.currentTimeMillis());
        return repository
                .save(warp)
                .thenApply(
                        v -> {
                            cache.put(name.toLowerCase(java.util.Locale.ROOT), warp);
                            return warp;
                        });
    }

    @Override
    public CompletableFuture<Boolean> delete(String name) {
        return repository
                .delete(name)
                .thenApply(
                        deleted -> {
                            if (deleted) {
                                cache.remove(name.toLowerCase(java.util.Locale.ROOT));
                            }
                            return deleted;
                        });
    }

    @Override
    public CompletableFuture<Warp> update(Warp warp) {
        return repository
                .save(warp)
                .thenApply(
                        v -> {
                            cache.put(warp.name().toLowerCase(java.util.Locale.ROOT), warp);
                            return warp;
                        });
    }

    @Override
    public CompletableFuture<Void> reload() {
        return warmCache();
    }
}

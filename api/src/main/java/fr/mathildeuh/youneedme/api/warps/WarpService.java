package fr.mathildeuh.youneedme.api.warps;

import fr.mathildeuh.youneedme.api.model.Position;
import fr.mathildeuh.youneedme.api.model.Warp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/** Server-wide warps: public or permission/cost-gated waypoints. */
public interface WarpService {

    CompletableFuture<List<Warp>> list();

    /**
     * Warps visible to a specific viewer (respects {@link Warp#hidden()} and per-warp permission).
     */
    CompletableFuture<List<Warp>> listVisibleTo(UUID viewer);

    CompletableFuture<@Nullable Warp> get(String name);

    CompletableFuture<Warp> create(String name, Position position, @Nullable UUID createdBy);

    CompletableFuture<Boolean> delete(String name);

    CompletableFuture<Warp> update(Warp warp);

    CompletableFuture<Void> reload();
}

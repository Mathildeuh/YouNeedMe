package fr.mathildeuh.youneedme.api.homes;

import fr.mathildeuh.youneedme.api.model.Home;
import fr.mathildeuh.youneedme.api.model.Position;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Per-player homes, including the permission-driven per-player home limit. */
public interface HomeService {

    CompletableFuture<List<Home>> list(UUID owner);

    CompletableFuture<Home> get(UUID owner, String name);

    /** Effective max number of homes for this player, from their highest {@code youneedme.homes.limit.<n>} node. */
    int limitFor(UUID owner);

    /**
     * Creates or overwrites a home. Fails (exceptionally, with {@link HomeLimitExceededException})
     * if the player is at their limit and {@code name} doesn't already exist for them.
     */
    CompletableFuture<Home> set(UUID owner, String name, Position position);

    CompletableFuture<Boolean> delete(UUID owner, String name);

    /** Thrown by {@link #set} when creating a new home would exceed {@link #limitFor}. */
    class HomeLimitExceededException extends RuntimeException {
        public final int limit;

        public HomeLimitExceededException(int limit) {
            super("Home limit of " + limit + " reached");
            this.limit = limit;
        }
    }
}

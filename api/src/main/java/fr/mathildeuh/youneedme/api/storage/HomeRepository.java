package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.model.Home;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HomeRepository {

    CompletableFuture<List<Home>> findByOwner(UUID owner);

    CompletableFuture<Optional<Home>> find(UUID owner, String name);

    CompletableFuture<Void> save(Home home);

    CompletableFuture<Boolean> delete(UUID owner, String name);

    CompletableFuture<Integer> count(UUID owner);
}

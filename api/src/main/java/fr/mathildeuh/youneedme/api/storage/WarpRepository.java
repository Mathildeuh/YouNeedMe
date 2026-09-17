package fr.mathildeuh.youneedme.api.storage;

import fr.mathildeuh.youneedme.api.model.Warp;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface WarpRepository {

    CompletableFuture<List<Warp>> findAll();

    CompletableFuture<Optional<Warp>> find(String name);

    CompletableFuture<Void> save(Warp warp);

    CompletableFuture<Boolean> delete(String name);
}

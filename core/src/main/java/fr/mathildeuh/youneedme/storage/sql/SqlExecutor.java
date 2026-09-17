package fr.mathildeuh.youneedme.storage.sql;

import fr.mathildeuh.youneedme.storage.StorageException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.sql.DataSource;

/**
 * Runs JDBC work off-thread and translates {@link SQLException} into an unchecked {@link
 * StorageException}.
 */
public final class SqlExecutor {

    private final DataSource dataSource;
    private final ExecutorService executor;

    public SqlExecutor(DataSource dataSource, ExecutorService executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    public <T> CompletableFuture<T> submit(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try (Connection connection = dataSource.getConnection()) {
                        return function.apply(connection);
                    } catch (SQLException e) {
                        throw new StorageException(e);
                    }
                },
                executor);
    }

    public CompletableFuture<Void> run(SqlAction action) {
        return submit(
                connection -> {
                    action.run(connection);
                    return null;
                });
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlAction {
        void run(Connection connection) throws SQLException;
    }
}

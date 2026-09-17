package fr.mathildeuh.youneedme.storage.sql;

import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Connection parameters for a {@link SqlStorage} backend - only the fields its dialect needs are
 * read.
 */
public record SqlConnectionConfig(
        @Nullable Path sqliteFile,
        String host,
        int port,
        String database,
        String username,
        String password,
        String extraParameters,
        int poolSize) {}

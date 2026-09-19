package fr.mathildeuh.youneedme.storage;

import java.io.Serial;

/**
 * Unchecked wrapper around a backend-specific failure (SQLException, MongoException, IOException,
 * ...).
 */
public class StorageException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(Throwable cause) {
        super(cause);
    }
}

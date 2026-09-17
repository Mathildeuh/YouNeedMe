package fr.mathildeuh.youneedme.storage;

/**
 * Unchecked wrapper around a backend-specific failure (SQLException, MongoException, IOException,
 * ...).
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(Throwable cause) {
        super(cause);
    }
}

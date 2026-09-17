package fr.mathildeuh.youneedme.api.storage;

/** Selectable value for {@code storage.type} in {@code config.yml}. */
public enum StorageType {
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRESQL,
    MONGODB,
    JSON
}

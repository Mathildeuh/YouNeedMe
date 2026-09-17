package fr.mathildeuh.youneedme.api.model;

import java.util.UUID;

/** A single player home. Immutable - "moving" or renaming a home produces a new instance. */
public record Home(UUID owner, String name, Position position, long createdAt, long updatedAt) {

    public Home withPosition(Position newPosition) {
        return new Home(owner, name, newPosition, createdAt, System.currentTimeMillis());
    }
}

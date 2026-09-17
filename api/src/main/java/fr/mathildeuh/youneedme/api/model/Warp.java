package fr.mathildeuh.youneedme.api.model;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** A server warp: public or permission-gated, optionally chargeable via Vault. */
public record Warp(
        String name,
        Position position,
        @Nullable String category,
        @Nullable String permission,
        double cost,
        @Nullable String description,
        boolean hidden,
        @Nullable UUID createdBy,
        long createdAt) {

    public Warp withPosition(Position newPosition) {
        return new Warp(
                name,
                newPosition,
                category,
                permission,
                cost,
                description,
                hidden,
                createdBy,
                createdAt);
    }

    public Warp withPermission(@Nullable String newPermission) {
        return new Warp(
                name,
                position,
                category,
                newPermission,
                cost,
                description,
                hidden,
                createdBy,
                createdAt);
    }

    public Warp withCost(double newCost) {
        return new Warp(
                name,
                position,
                category,
                permission,
                newCost,
                description,
                hidden,
                createdBy,
                createdAt);
    }

    public Warp withDescription(@Nullable String newDescription) {
        return new Warp(
                name,
                position,
                category,
                permission,
                cost,
                newDescription,
                hidden,
                createdBy,
                createdAt);
    }

    public Warp withCategory(@Nullable String newCategory) {
        return new Warp(
                name,
                position,
                newCategory,
                permission,
                cost,
                description,
                hidden,
                createdBy,
                createdAt);
    }

    public Warp withHidden(boolean newHidden) {
        return new Warp(
                name,
                position,
                category,
                permission,
                cost,
                description,
                newHidden,
                createdBy,
                createdAt);
    }
}

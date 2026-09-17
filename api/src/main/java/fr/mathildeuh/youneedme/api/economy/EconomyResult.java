package fr.mathildeuh.youneedme.api.economy;

import org.jetbrains.annotations.Nullable;

/** Outcome of a single economy operation, mirroring Vault's own success/failure-with-reason shape. */
public record EconomyResult(Status status, double balanceAfter, @Nullable String message) {

    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        BELOW_MINIMUM,
        ABOVE_MAXIMUM,
        ACCOUNT_NOT_FOUND,
        ERROR
    }

    public static EconomyResult success(double balanceAfter) {
        return new EconomyResult(Status.SUCCESS, balanceAfter, null);
    }

    public static EconomyResult failure(Status status, double currentBalance, String message) {
        return new EconomyResult(status, currentBalance, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}

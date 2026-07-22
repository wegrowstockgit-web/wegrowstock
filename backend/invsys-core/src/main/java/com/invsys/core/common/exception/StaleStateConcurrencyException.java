package com.invsys.core.common.exception;

public class StaleStateConcurrencyException extends SystemFailureException {

    public StaleStateConcurrencyException(String message) {
        super("STALE_STATE_CONCURRENCY", message);
    }

    public StaleStateConcurrencyException(String message, Throwable cause) {
        super("STALE_STATE_CONCURRENCY", message, cause);
    }

    public StaleStateConcurrencyException() {
        this("Concurrent modification detected; retry the operation");
    }
}

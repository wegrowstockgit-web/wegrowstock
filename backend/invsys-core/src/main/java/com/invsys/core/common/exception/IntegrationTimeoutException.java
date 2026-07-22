package com.invsys.core.common.exception;

public class IntegrationTimeoutException extends SystemFailureException {

    public IntegrationTimeoutException(String message) {
        super("INTEGRATION_TIMEOUT", message);
    }

    public IntegrationTimeoutException(String message, Throwable cause) {
        super("INTEGRATION_TIMEOUT", message, cause);
    }

    public IntegrationTimeoutException() {
        this("Upstream integration timed out");
    }
}

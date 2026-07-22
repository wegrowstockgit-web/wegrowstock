package com.invsys.core.common.exception;

/**
 * Infrastructure / runtime failures. Never leak stack traces or internal detail to clients.
 * Mapped to HTTP 500 with a generic message.
 */
public abstract class SystemFailureException extends RuntimeException {

    private final String code;

    protected SystemFailureException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected SystemFailureException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

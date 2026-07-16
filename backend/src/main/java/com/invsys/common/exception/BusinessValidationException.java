package com.invsys.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain / business-rule failures safe to surface to clients (RFC 7807 detail).
 * Mapped to 4xx (typically 409 Conflict or 422 Unprocessable Entity).
 */
public abstract class BusinessValidationException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected BusinessValidationException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    protected BusinessValidationException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

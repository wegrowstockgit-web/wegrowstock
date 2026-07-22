package com.invsys.core.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidAllocationException extends BusinessValidationException {

    public InvalidAllocationException(String message) {
        super("INVALID_ALLOCATION", HttpStatus.CONFLICT, message);
    }

    public InvalidAllocationException() {
        this("Allocation is not valid for the requested operation");
    }
}

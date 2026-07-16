package com.invsys.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessValidationException {

    public InsufficientStockException(String message) {
        super("INSUFFICIENT_STOCK", HttpStatus.CONFLICT, message);
    }

    public InsufficientStockException() {
        this("Insufficient available inventory");
    }
}

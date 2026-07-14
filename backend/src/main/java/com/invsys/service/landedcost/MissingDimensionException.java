package com.invsys.service.landedcost;

/**
 * Thrown when a dimensional strategy cannot resolve a positive basis for every line.
 */
public class MissingDimensionException extends RuntimeException {

    public MissingDimensionException(String message) {
        super(message);
    }
}

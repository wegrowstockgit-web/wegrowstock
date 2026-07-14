package com.invsys.common;

public record PageRequest(String cursor, int limit) {
    public PageRequest {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
    }
}

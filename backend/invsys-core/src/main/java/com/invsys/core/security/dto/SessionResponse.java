package com.invsys.core.security.dto;

import java.util.List;
import java.util.UUID;

/**
 * Authenticated session metadata returned after login/refresh.
 * Access and refresh JWTs are delivered exclusively via HttpOnly cookies.
 */
public record SessionResponse(
        UUID tenantId,
        UUID userId,
        List<String> roles,
        List<UUID> warehouseIds,
        String avatarUrl
) {
    public static SessionResponse from(TokenResponse tokens) {
        return new SessionResponse(
                tokens.tenantId(),
                tokens.userId(),
                tokens.roles(),
                tokens.warehouseIds() != null ? tokens.warehouseIds() : List.of(),
                tokens.avatarUrl());
    }
}

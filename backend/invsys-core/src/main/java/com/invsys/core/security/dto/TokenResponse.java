package com.invsys.core.security.dto;

import java.util.List;
import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        UUID tenantId,
        UUID userId,
        List<String> roles,
        List<UUID> warehouseIds,
        String avatarUrl
) {
    public TokenResponse(String accessToken, String refreshToken, UUID tenantId, UUID userId, List<String> roles) {
        this(accessToken, refreshToken, tenantId, userId, roles, List.of(), null);
    }

    public TokenResponse(String accessToken, String refreshToken, UUID tenantId, UUID userId,
                         List<String> roles, List<UUID> warehouseIds) {
        this(accessToken, refreshToken, tenantId, userId, roles, warehouseIds, null);
    }
}

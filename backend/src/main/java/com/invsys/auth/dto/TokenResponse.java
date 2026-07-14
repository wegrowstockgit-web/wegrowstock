package com.invsys.auth.dto;

import java.util.List;
import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        UUID tenantId,
        UUID userId,
        List<String> roles,
        List<UUID> warehouseIds
) {
    public TokenResponse(String accessToken, String refreshToken, UUID tenantId, UUID userId, List<String> roles) {
        this(accessToken, refreshToken, tenantId, userId, roles, List.of());
    }
}

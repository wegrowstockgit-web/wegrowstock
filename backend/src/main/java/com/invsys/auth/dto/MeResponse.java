package com.invsys.auth.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        UUID tenantId,
        String email,
        String displayName,
        List<String> roles,
        List<UUID> warehouseIds,
        String avatarUrl
) {
}

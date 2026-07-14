package com.invsys.auth.dto;

import java.util.List;
import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        UUID tenantId,
        UUID userId,
        List<String> roles
) {
}

package com.invsys.core.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.UUID;

/**
 * Temporary operator context after PIN pad switch. Access JWT is set as HttpOnly cookie;
 * the JSON body never includes the raw token.
 */
public record TerminalSwitchResponse(
        @JsonIgnore String accessToken,
        UUID tenantId,
        UUID userId,
        List<String> roles,
        List<UUID> warehouseIds,
        int expiresInSeconds,
        String tokenType,
        UUID switchedFromUserId
) {
    public TerminalSwitchResponse withoutAccessToken() {
        return new TerminalSwitchResponse(
                null, tenantId, userId, roles, warehouseIds, expiresInSeconds, tokenType, switchedFromUserId);
    }
}

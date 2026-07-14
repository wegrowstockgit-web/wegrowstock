package com.invsys.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * Temporary operator context after PIN pad switch. No refresh token is issued —
 * the primary device session (refresh token) stays on the client.
 */
public record TerminalSwitchResponse(
        String accessToken,
        UUID tenantId,
        UUID userId,
        List<String> roles,
        List<UUID> warehouseIds,
        int expiresInSeconds,
        String tokenType,
        UUID switchedFromUserId
) {
}

package com.invsys.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Public channel view — never includes decrypted secrets.
 */
public record IntegrationChannelResponse(
        UUID id,
        String channelType,
        String status,
        boolean credentialsConfigured,
        Map<String, Object> settings,
        Instant createdAt,
        Instant updatedAt
) {
}

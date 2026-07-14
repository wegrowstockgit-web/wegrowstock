package com.invsys.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SyncLogResponse(
        UUID id,
        String system,
        String entityType,
        UUID entityId,
        String status,
        int retryCount,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}

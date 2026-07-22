package com.invsys.core.integration;

import com.invsys.core.integration.OutboxEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OutboxEventRepositoryCustom {
    List<ClaimedOutboxEvent> claimPendingEvents(int limit);

    void markPublished(UUID id);

    void markFailed(UUID id, int retryCount, String lastError, java.time.Instant nextAttemptAt, String status);

    record ClaimedOutboxEvent(
            UUID id,
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload,
            int retryCount
    ) {
    }
}

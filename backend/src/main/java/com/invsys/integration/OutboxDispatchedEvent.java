package com.invsys.integration;

import java.util.Map;
import java.util.UUID;

/**
 * Fired after an outbox row is claimed and marked published (or skipped with no handler).
 * Projection / SSE listeners subscribe without competing for the single handler slot
 * per {@code eventType} in {@link OutboxDispatcher}.
 */
public record OutboxDispatchedEvent(
        UUID tenantId,
        UUID eventId,
        String eventType,
        UUID aggregateId,
        Map<String, Object> payload
) {
}

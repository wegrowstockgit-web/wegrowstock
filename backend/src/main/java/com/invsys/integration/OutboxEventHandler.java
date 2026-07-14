package com.invsys.integration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OutboxEventHandler {
    String eventType();

    default List<String> eventTypes() {
        return List.of(eventType());
    }

    void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) throws Exception;
}

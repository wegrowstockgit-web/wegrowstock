package com.invsys.integration;

import com.invsys.domain.OutboxEvent;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(TenantContext.requireTenantId());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>());
        event.setStatus("PENDING");
        return outboxEventRepository.save(event);
    }
}

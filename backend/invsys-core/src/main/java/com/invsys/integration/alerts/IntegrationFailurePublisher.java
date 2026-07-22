package com.invsys.integration.alerts;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IntegrationFailurePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public IntegrationFailurePublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(UUID tenantId, String system, String reason, String detail) {
        if (tenantId == null || system == null || system.isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new IntegrationFailureEvent(tenantId, system, reason, detail));
    }

    public void publish(UUID tenantId, String system, String reason, String detail, UUID entityId) {
        if (tenantId == null || system == null || system.isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new IntegrationFailureEvent(tenantId, system, reason, detail, entityId));
    }

    public void publishForced(UUID tenantId, String system, String reason, String detail) {
        eventPublisher.publishEvent(new IntegrationFailureEvent(
                tenantId, system, reason, detail, null, true));
    }
}

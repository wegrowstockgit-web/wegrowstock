package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.AuditLog;
import com.invsys.domain.OutboxEvent;
import com.invsys.domain.PlatformAlert;
import com.invsys.integration.IntegrationSettingsService;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationsConsoleService {

    private final OutboxEventRepository outboxEventRepository;
    private final IntegrationSettingsService integrationSettingsService;
    private final PlatformAlertService platformAlertService;
    private final AuditService auditService;

    public OperationsConsoleService(OutboxEventRepository outboxEventRepository,
                                    IntegrationSettingsService integrationSettingsService,
                                    PlatformAlertService platformAlertService,
                                    AuditService auditService) {
        this.outboxEventRepository = outboxEventRepository;
        this.integrationSettingsService = integrationSettingsService;
        this.platformAlertService = platformAlertService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> failedOutbox() {
        return outboxEventRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                TenantContext.requireTenantId(), "FAILED");
    }

    @Transactional
    public OutboxEvent retryOutbox(UUID eventId) {
        OutboxEvent event = requireOutbox(eventId);
        event.setStatus("PENDING");
        event.setNextAttemptAt(null);
        event.setLastError(null);
        event.setPublishedAt(null);
        OutboxEvent saved = outboxEventRepository.save(event);
        auditService.record("OUTBOX_RETRY", "OUTBOX_EVENT", saved.getId(), Map.of(
                "eventType", saved.getEventType(),
                "retryCount", saved.getRetryCount()));
        return saved;
    }

    @Transactional
    public OutboxEvent editPayloadAndRetry(UUID eventId, Map<String, Object> payload) {
        OutboxEvent event = requireOutbox(eventId);
        Map<String, Object> before = new LinkedHashMap<>(event.getPayload());
        event.setPayload(payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>());
        event.setStatus("PENDING");
        event.setNextAttemptAt(null);
        event.setLastError(null);
        event.setPublishedAt(null);
        OutboxEvent saved = outboxEventRepository.save(event);
        auditService.record("OUTBOX_EDIT_RETRY", "OUTBOX_EVENT", saved.getId(), Map.of(
                "before", before,
                "after", saved.getPayload(),
                "eventType", saved.getEventType()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<com.invsys.api.dto.SyncLogResponse> failedSyncLogs() {
        return integrationSettingsService.listSyncLogs(null, "FAILED");
    }

    public com.invsys.api.dto.SyncLogResponse retrySyncLog(UUID id) {
        var response = integrationSettingsService.retry(id);
        auditService.record("SYNC_LOG_RETRY", "INTEGRATION_SYNC_LOG", id, Map.of(
                "system", response.system(),
                "entityType", response.entityType()));
        return response;
    }

    public List<PlatformAlert> openAlerts() {
        return platformAlertService.listOpen();
    }

    public PlatformAlert acknowledgeAlert(UUID id) {
        return platformAlertService.acknowledge(id);
    }

    public List<AuditLog> recentAudit() {
        return auditService.recent();
    }

    private OutboxEvent requireOutbox(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Outbox event not found"));
        if (!"FAILED".equals(event.getStatus()) && !"PENDING".equals(event.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Only FAILED/PENDING outbox events can be retried");
        }
        return event;
    }
}

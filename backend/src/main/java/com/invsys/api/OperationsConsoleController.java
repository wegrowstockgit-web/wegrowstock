package com.invsys.api;

import com.invsys.api.dto.SyncLogResponse;
import com.invsys.domain.AuditLog;
import com.invsys.domain.OutboxEvent;
import com.invsys.domain.PlatformAlert;
import com.invsys.service.OperationsConsoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class OperationsConsoleController {

    private final OperationsConsoleService operationsConsoleService;

    public OperationsConsoleController(OperationsConsoleService operationsConsoleService) {
        this.operationsConsoleService = operationsConsoleService;
    }

    @GetMapping("/outbox/failed")
    public List<OutboxEventResponse> failedOutbox() {
        return operationsConsoleService.failedOutbox().stream().map(this::toOutbox).toList();
    }

    @PostMapping("/outbox/{id}/retry")
    public OutboxEventResponse retryOutbox(@PathVariable UUID id) {
        return toOutbox(operationsConsoleService.retryOutbox(id));
    }

    @PutMapping("/outbox/{id}/payload")
    public OutboxEventResponse editOutboxPayload(@PathVariable UUID id,
                                                 @Valid @RequestBody EditPayloadRequest body) {
        return toOutbox(operationsConsoleService.editPayloadAndRetry(id, body.payload()));
    }

    @GetMapping("/sync-logs/failed")
    public List<SyncLogResponse> failedSyncLogs() {
        return operationsConsoleService.failedSyncLogs();
    }

    @PostMapping("/sync-logs/{id}/retry")
    public SyncLogResponse retrySyncLog(@PathVariable UUID id) {
        return operationsConsoleService.retrySyncLog(id);
    }

    @GetMapping("/alerts")
    public List<AlertResponse> alerts() {
        return operationsConsoleService.openAlerts().stream().map(this::toAlert).toList();
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public AlertResponse acknowledge(@PathVariable UUID id) {
        return toAlert(operationsConsoleService.acknowledgeAlert(id));
    }

    @GetMapping("/audit")
    public List<AuditResponse> audit() {
        return operationsConsoleService.recentAudit().stream().map(this::toAudit).toList();
    }

    private OutboxEventResponse toOutbox(OutboxEvent e) {
        return new OutboxEventResponse(
                e.getId(),
                e.getAggregateType(),
                e.getAggregateId(),
                e.getEventType(),
                e.getPayload(),
                e.getStatus(),
                e.getRetryCount(),
                e.getLastError(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private AlertResponse toAlert(PlatformAlert a) {
        return new AlertResponse(
                a.getId(),
                a.getAlertType(),
                a.getSeverity(),
                a.getSourceSystem(),
                a.getTitle(),
                a.getDetails(),
                a.getAcknowledgedAt(),
                a.getCreatedAt());
    }

    private AuditResponse toAudit(AuditLog a) {
        return new AuditResponse(
                a.getId(),
                a.getActorUserId(),
                a.getAction(),
                a.getEntityType(),
                a.getEntityId(),
                a.getDiff(),
                a.getCreatedAt());
    }

    public record EditPayloadRequest(@NotNull Map<String, Object> payload) {
    }

    public record OutboxEventResponse(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload,
            String status,
            int retryCount,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AlertResponse(
            UUID id,
            String alertType,
            String severity,
            String sourceSystem,
            String title,
            Map<String, Object> details,
            Instant acknowledgedAt,
            Instant createdAt
    ) {
    }

    public record AuditResponse(
            UUID id,
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> diff,
            Instant createdAt
    ) {
    }
}

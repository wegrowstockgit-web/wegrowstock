package com.invsys.service;

import com.invsys.domain.IntegrationChannel;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.integration.channel.SyncDirection;
import com.invsys.integration.channel.SyncEntityType;
import com.invsys.integration.channel.SyncLogStatus;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records Phase-1 hub synchronization history against {@code integration_sync_logs}.
 */
@Service
public class IntegrationSyncHistoryService {

    private final IntegrationSyncLogRepository syncLogRepository;

    public IntegrationSyncHistoryService(IntegrationSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    @Transactional
    public IntegrationSyncLog record(IntegrationChannel channel,
                                     SyncDirection direction,
                                     SyncEntityType entityType,
                                     String externalId,
                                     SyncLogStatus status,
                                     Map<String, Object> payloadSummary,
                                     String errorMessage) {
        return record(channel, null, direction, entityType, externalId, null, status, payloadSummary, errorMessage);
    }

    @Transactional
    public IntegrationSyncLog record(IntegrationChannel channel,
                                     String systemFallback,
                                     SyncDirection direction,
                                     SyncEntityType entityType,
                                     String externalId,
                                     UUID entityId,
                                     SyncLogStatus status,
                                     Map<String, Object> payloadSummary,
                                     String errorMessage) {
        UUID tenantId = TenantContext.requireTenantId();
        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setTenantId(tenantId);
        if (channel != null) {
            log.setChannelId(channel.getId());
            log.setSystem(channel.getChannelType().name());
        } else {
            log.setSystem(systemFallback != null && !systemFallback.isBlank() ? systemFallback : "UNKNOWN");
        }
        log.setDirection(direction);
        log.setEntityType(entityType.name());
        log.setExternalId(externalId);
        log.setEntityId(entityId);
        log.setStatus(status.name());
        log.setPayloadSummary(payloadSummary != null ? new LinkedHashMap<>(payloadSummary) : Map.of());
        if (errorMessage != null && !errorMessage.isBlank()) {
            log.setErrorMessage(errorMessage);
        }
        if (status == SyncLogStatus.SUCCESS
                || status == SyncLogStatus.FAILED
                || status == SyncLogStatus.WARNING
                || status == SyncLogStatus.SYNCED
                || status == SyncLogStatus.SKIPPED) {
            log.setProcessedAt(Instant.now());
        }
        return syncLogRepository.save(log);
    }

    /** Survives rollback of the caller transaction (failed inbound attempts). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IntegrationSyncLog recordIsolated(IntegrationChannel channel,
                                             String systemFallback,
                                             SyncDirection direction,
                                             SyncEntityType entityType,
                                             String externalId,
                                             SyncLogStatus status,
                                             Map<String, Object> payloadSummary,
                                             String errorMessage) {
        return record(channel, systemFallback, direction, entityType, externalId, null, status, payloadSummary, errorMessage);
    }
}

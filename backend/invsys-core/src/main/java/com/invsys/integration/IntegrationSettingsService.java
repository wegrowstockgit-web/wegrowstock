package com.invsys.integration;

import com.invsys.api.dto.SyncLogResponse;
import com.invsys.core.common.ApiException;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.integration.OutboxService;

@Service
public class IntegrationSettingsService {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final OutboxService outboxService;

    public IntegrationSettingsService(IntegrationSyncLogRepository syncLogRepository,
                                    OutboxService outboxService) {
        this.syncLogRepository = syncLogRepository;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true)
    public List<SyncLogResponse> listSyncLogs(String system, String status) {
        UUID tenantId = TenantContext.requireTenantId();
        List<IntegrationSyncLog> logs;
        if (system != null && status != null) {
            logs = syncLogRepository.findByTenantIdAndSystemAndStatusOrderByCreatedAtDesc(tenantId, system, status);
        } else if (system != null) {
            logs = syncLogRepository.findByTenantIdAndSystemOrderByCreatedAtDesc(tenantId, system);
        } else if (status != null) {
            logs = syncLogRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        } else {
            logs = syncLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return logs.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SyncLogResponse retry(UUID syncLogId) {
        IntegrationSyncLog log = syncLogRepository.findById(syncLogId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sync log not found"));

        if (log.getEntityId() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RETRY_UNSUPPORTED",
                    "Sync log has no internal entity id to retry");
        }

        log.setStatus("PENDING");
        log.setLastError(null);
        log.setErrorMessage(null);
        syncLogRepository.save(log);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system", log.getSystem());
        payload.put("entityType", log.getEntityType());
        payload.put("retry", true);

        String eventType = eventTypeForEntity(log.getEntityType());
        outboxService.append(log.getEntityType(), log.getEntityId(), eventType, payload);

        return toResponse(log);
    }

    private String eventTypeForEntity(String entityType) {
        return switch (entityType) {
            case "LEDGER_ENTRY" -> "LEDGER_ENTRY_ARRIVED";
            case "STOCK_LEVEL" -> "STOCK_LEVEL_CHANGED";
            case "INVOICE" -> "INVOICE_SYNC";
            case "SHIPMENT" -> "EASYPOST_LABEL";
            default -> "LEDGER_ENTRY_ARRIVED";
        };
    }

    private SyncLogResponse toResponse(IntegrationSyncLog log) {
        return new SyncLogResponse(
                log.getId(),
                log.getSystem(),
                log.getEntityType(),
                log.getEntityId(),
                log.getStatus(),
                log.getRetryCount(),
                log.getLastError(),
                log.getCreatedAt(),
                log.getUpdatedAt());
    }
}

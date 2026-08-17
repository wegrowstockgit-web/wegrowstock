package com.invsys.pos;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.pos.dto.PosAuditEventDto;
import com.invsys.pos.dto.PosAuditSyncResponse;
import com.invsys.pos.dto.PosAuditSyncResponse.RejectedEvent;
import com.invsys.repository.AuditLogRepository;
import com.invsys.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ingests offline POS exception events into the immutable tenant {@code audit_log}
 * used by Loss Prevention / Exception-Based Reporting.
 */
@Service
public class PosAuditSyncService {

    static final Set<String> EVENT_TYPES = Set.of("LINE_VOID", "TX_VOID", "NO_SALE", "PRICE_OVERRIDE");

    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;

    public PosAuditSyncService(AuditService auditService, AuditLogRepository auditLogRepository) {
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public PosAuditSyncResponse sync(List<PosAuditEventDto> events) {
        UUID tenantId = TenantContext.requireTenantId();
        if (events == null || events.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_BATCH", "Audit event batch is required.");
        }
        int accepted = 0;
        int duplicates = 0;
        List<RejectedEvent> rejected = new ArrayList<>();
        for (PosAuditEventDto event : events) {
            try {
                if (persist(tenantId, event)) {
                    accepted++;
                } else {
                    duplicates++;
                }
            } catch (ApiException ex) {
                UUID id = event == null ? null : event.id();
                rejected.add(new RejectedEvent(id, ex.getMessage()));
            }
        }
        return new PosAuditSyncResponse(accepted, duplicates, rejected);
    }

    private boolean persist(UUID tenantId, PosAuditEventDto event) {
        if (event == null || event.id() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "Event id is required.");
        }
        String eventType = normalizeType(event.eventType());
        if (!EVENT_TYPES.contains(eventType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_TYPE",
                    "Unsupported POS exception type.");
        }
        if (auditLogRepository.existsByTenantIdAndEntityId(tenantId, event.id())) {
            return false;
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("eventId", event.id().toString());
        diff.put("eventType", eventType);
        diff.put("cashierId", event.cashierId());
        diff.put("orderId", event.orderId());
        diff.put("productId", event.productId());
        diff.put("valueVoided", event.valueVoided() == null ? null : event.valueVoided().toPlainString());
        diff.put("managerOverrideId", event.managerOverrideId());
        diff.put("occurredAt", event.timestamp());
        diff.put("source", "POS_OFFLINE_AUDIT");
        auditService.record("POS_" + eventType, "POS_EXCEPTION", event.id(), diff);
        return true;
    }

    private static String normalizeType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }
}

package com.invsys.service;

import com.invsys.common.MdcSupport;
import com.invsys.domain.AuditLog;
import com.invsys.repository.AuditLogRepository;
import com.invsys.tenancy.TenantContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog record(String action, String entityType, UUID entityId, Map<String, Object> diff) {
        AuditLog entry = new AuditLog();
        entry.setTenantId(TenantContext.requireTenantId());
        entry.setActorUserId(TenantContext.getUserId().orElse(null));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        Map<String, Object> payload = diff != null ? new LinkedHashMap<>(diff) : new LinkedHashMap<>();
        String requestId = MDC.get(MdcSupport.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = MDC.get("requestId");
        }
        if (requestId != null && !requestId.isBlank() && !payload.containsKey("requestId")) {
            payload.put("requestId", requestId);
        }
        entry.setDiff(payload);
        return auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent() {
        return auditLogRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }
}

package com.invsys.service;

import com.invsys.domain.AuditLog;
import com.invsys.repository.AuditLogRepository;
import com.invsys.tenancy.TenantContext;
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
        entry.setDiff(diff != null ? new LinkedHashMap<>(diff) : new LinkedHashMap<>());
        return auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent() {
        return auditLogRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }
}

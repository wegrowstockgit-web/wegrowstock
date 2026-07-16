package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.OfflineSyncConflict;
import com.invsys.repository.OfflineSyncConflictRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OfflineSyncConflictService {

    private final OfflineSyncConflictRepository repository;

    public OfflineSyncConflictService(OfflineSyncConflictRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OfflineSyncConflict sink(Map<String, Object> payload, String errorMessage) {
        OfflineSyncConflict row = new OfflineSyncConflict();
        row.setTenantId(TenantContext.requireTenantId());
        row.setPayload(payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>());
        row.setErrorMessage(errorMessage);
        row.setStatus("PENDING");
        return repository.save(row);
    }

    @Transactional(readOnly = true)
    public List<OfflineSyncConflict> list(String status) {
        UUID tenantId = TenantContext.requireTenantId();
        if (status != null && !status.isBlank()) {
            return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status.trim().toUpperCase());
        }
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public OfflineSyncConflict dismiss(UUID id) {
        OfflineSyncConflict row = require(id);
        row.setStatus("DISMISSED");
        return repository.save(row);
    }

    /**
     * Marks the conflict for client-side re-enqueue (new Idempotency-Key) or clears when resolved.
     */
    @Transactional
    public OfflineSyncConflict forceRetry(UUID id) {
        OfflineSyncConflict row = require(id);
        if (!"PENDING".equals(row.getStatus()) && !"RETRY_REQUESTED".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT_NOT_RETRYABLE",
                    "Only pending conflicts can be force-retried");
        }
        row.setStatus("RETRY_REQUESTED");
        return repository.save(row);
    }

    @Transactional
    public OfflineSyncConflict markResolved(UUID id) {
        OfflineSyncConflict row = require(id);
        row.setStatus("RESOLVED");
        return repository.save(row);
    }

    private OfflineSyncConflict require(UUID id) {
        return repository.findByTenantIdAndId(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONFLICT_NOT_FOUND",
                        "Offline sync conflict not found"));
    }
}

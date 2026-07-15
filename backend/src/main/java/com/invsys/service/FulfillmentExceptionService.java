package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.FulfillmentException;
import com.invsys.domain.Lot;
import com.invsys.domain.PickingTask;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.FulfillmentExceptionRepository;
import com.invsys.repository.LotRepository;
import com.invsys.repository.PickingTaskRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Shunts damaged lot-tracked barcodes off the pick path without inventory_ledger writes.
 * Allocation status change releases ATP hold via the existing allocations→levels trigger.
 */
@Service
public class FulfillmentExceptionService {

    public static final String STATUS_EXCEPTION = "EXCEPTION_DAMAGED_BARCODE";

    private final AllocationRepository allocationRepository;
    private final FulfillmentExceptionRepository exceptionRepository;
    private final PickingTaskRepository pickingTaskRepository;
    private final LotRepository lotRepository;
    private final Executor virtualThreadExecutor;
    private final TransactionTemplate transactionTemplate;

    public FulfillmentExceptionService(AllocationRepository allocationRepository,
                                       FulfillmentExceptionRepository exceptionRepository,
                                       PickingTaskRepository pickingTaskRepository,
                                       LotRepository lotRepository,
                                       @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor,
                                       PlatformTransactionManager transactionManager) {
        this.allocationRepository = allocationRepository;
        this.exceptionRepository = exceptionRepository;
        this.pickingTaskRepository = pickingTaskRepository;
        this.lotRepository = lotRepository;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public ReportResult reportDamagedBarcode(UUID allocationId, Map<String, Object> metadata) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));

        Allocation allocation = allocationRepository.findByTenantIdAndId(tenantId, allocationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Allocation not found"));

        String status = allocation.getStatus() == null ? "" : allocation.getStatus().toUpperCase();
        if (STATUS_EXCEPTION.equals(status)) {
            FulfillmentException existing = exceptionRepository
                    .findFirstByTenantIdAndAllocationIdAndResolutionStatus(tenantId, allocationId, "OPEN")
                    .orElseGet(() -> exceptionRepository
                            .findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                            .filter(e -> allocationId.equals(e.getAllocationId()))
                            .findFirst()
                            .orElseThrow());
            return new ReportResult(existing.getId(), allocation.getId(), existing.getResolutionStatus(), true);
        }
        if (!"ACTIVE".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALLOCATION_NOT_ACTIVE",
                    "Only ACTIVE allocations can be shunted as damaged barcode exceptions");
        }

        // Status leave ACTIVE → trigger releases allocated qty; no ledger write.
        allocation.setStatus(STATUS_EXCEPTION);
        allocation.setAssignedToUserId(null);
        allocationRepository.save(allocation);

        for (PickingTask task : pickingTaskRepository.findByTenantIdAndAllocationId(tenantId, allocationId)) {
            if ("PENDING".equalsIgnoreCase(task.getStatus())) {
                task.setStatus("SKIPPED");
                pickingTaskRepository.save(task);
            }
        }

        UUID warehouseId = TenantContext.getWarehouseId().orElse(allocation.getLocationId());
        FulfillmentException exception = new FulfillmentException();
        exception.setTenantId(tenantId);
        exception.setAllocationId(allocationId);
        exception.setReportedBy(userId);
        exception.setWarehouseId(warehouseId);
        exception.setResolutionStatus("OPEN");
        Map<String, Object> meta = new LinkedHashMap<>();
        if (metadata != null) {
            meta.putAll(metadata);
        }
        meta.putIfAbsent("reason", "DAMAGED_BARCODE");
        meta.put("variantId", allocation.getVariantId().toString());
        meta.put("locationId", allocation.getLocationId().toString());
        exception.setMetadata(meta);
        exception = exceptionRepository.save(exception);

        return new ReportResult(exception.getId(), allocation.getId(), exception.getResolutionStatus(), false);
    }

    @Transactional(readOnly = true)
    public List<FulfillmentException> list(String resolutionStatus) {
        UUID tenantId = TenantContext.requireTenantId();
        if (resolutionStatus != null && !resolutionStatus.isBlank()) {
            return exceptionRepository.findByTenantIdAndResolutionStatusOrderByCreatedAtDesc(
                    tenantId, resolutionStatus.trim().toUpperCase());
        }
        return exceptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public FulfillmentException resolve(UUID exceptionId, ResolveRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        FulfillmentException exception = exceptionRepository.findByTenantIdAndId(tenantId, exceptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Exception not found"));
        if (!"OPEN".equalsIgnoreCase(exception.getResolutionStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RESOLVED", "Exception is not OPEN");
        }

        String action = request.action() != null ? request.action().trim().toUpperCase() : "CLEAR";
        Map<String, Object> meta = new LinkedHashMap<>(exception.getMetadata());
        meta.put("resolvedAction", action);
        meta.put("resolvedAt", Instant.now().toString());
        if (request.notes() != null && !request.notes().isBlank()) {
            meta.put("resolveNotes", request.notes().trim());
        }

        Allocation allocation = allocationRepository.findByTenantIdAndId(tenantId, exception.getAllocationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Allocation not found"));

        switch (action) {
            case "DISCARD" -> {
                exception.setResolutionStatus("DISCARDED");
                meta.put("allocationStatus", allocation.getStatus());
            }
            case "LOT_OVERRIDE" -> {
                if (request.lotNumber() == null || request.lotNumber().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                            "lotNumber is required for LOT_OVERRIDE");
                }
                exception.setResolutionStatus("RESOLVED");
                meta.put("manualLotOverride", request.lotNumber().trim());
                UUID tenant = tenantId;
                UUID allocationId = allocation.getId();
                String lotNumber = request.lotNumber().trim();
                // Manual lot bind on a virtual thread so the manager UI stays snappy.
                virtualThreadExecutor.execute(() -> applyLotOverrideAsync(tenant, allocationId, lotNumber));
            }
            case "CLEAR" -> {
                exception.setResolutionStatus("RESOLVED");
                if (STATUS_EXCEPTION.equalsIgnoreCase(allocation.getStatus())) {
                    allocation.setStatus("ACTIVE");
                    allocationRepository.save(allocation);
                }
                meta.put("allocationReactivated", true);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Unknown action. Use CLEAR, DISCARD, or LOT_OVERRIDE");
        }

        exception.setMetadata(meta);
        return exceptionRepository.save(exception);
    }

    private void applyLotOverrideAsync(UUID tenantId, UUID allocationId, String lotNumber) {
        TenantContext.setTenantId(tenantId);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Allocation allocation = allocationRepository.findByTenantIdAndId(tenantId, allocationId).orElse(null);
                if (allocation == null) {
                    return;
                }
                Lot lot = lotRepository.findByTenantIdAndVariantIdAndLotNumber(
                                tenantId, allocation.getVariantId(), lotNumber)
                        .orElseGet(() -> {
                            Lot created = new Lot();
                            created.setTenantId(tenantId);
                            created.setVariantId(allocation.getVariantId());
                            created.setLotNumber(lotNumber);
                            created.setReceivedAt(Instant.now());
                            return lotRepository.save(created);
                        });
                allocation.setLotId(lot.getId());
                if (STATUS_EXCEPTION.equalsIgnoreCase(allocation.getStatus())) {
                    allocation.setStatus("ACTIVE");
                }
                allocationRepository.save(allocation);
            });
        } finally {
            TenantContext.clear();
        }
    }

    public record ReportResult(UUID exceptionId, UUID allocationId, String resolutionStatus, boolean alreadyReported) {
    }

    public record ResolveRequest(String action, String lotNumber, String notes) {
    }
}

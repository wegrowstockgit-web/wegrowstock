package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AllocationService {

    private final InventoryLevelRepository levelRepository;
    private final AllocationRepository allocationRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final KitService kitService;

    public AllocationService(InventoryLevelRepository levelRepository,
                             AllocationRepository allocationRepository,
                             SalesOrderLineRepository salesOrderLineRepository,
                             KitService kitService) {
        this.levelRepository = levelRepository;
        this.allocationRepository = allocationRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.kitService = kitService;
    }

    @Transactional
    public List<Allocation> allocate(SalesOrderLine line, List<UUID> locationIds) {
        if (kitService.isKit(line.getVariantId())) {
            return allocateKit(line, locationIds);
        }
        return allocateStandard(line, locationIds);
    }

    private List<Allocation> allocateStandard(SalesOrderLine line, List<UUID> locationIds) {
        BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyAllocated());
        List<Allocation> created = new ArrayList<>();
        if (remaining.signum() <= 0) {
            return created;
        }

        allocateQuantity(line, line.getVariantId(), remaining, locationIds, created);
        finalizeLineAllocation(line, created);
        return created;
    }

    private List<Allocation> allocateKit(SalesOrderLine line, List<UUID> locationIds) {
        BigDecimal remainingKits = line.getQtyOrdered().subtract(line.getQtyAllocated());
        List<Allocation> created = new ArrayList<>();
        if (remainingKits.signum() <= 0) {
            return created;
        }

        List<KitService.BomComponent> components = kitService.explodeComponents(line.getVariantId());
        UUID tenantId = TenantContext.requireTenantId();

        BigDecimal kitsToAllocate = remainingKits;
        for (KitService.BomComponent component : components) {
            List<InventoryLevel> levels = levelRepository.findAvailableForAllocation(
                    tenantId, component.variantId(), locationIds);
            BigDecimal available = levels.stream()
                    .map(InventoryLevel::getAvailable)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal maxKits = available.divide(component.quantityPerParent(), 0, RoundingMode.DOWN);
            kitsToAllocate = kitsToAllocate.min(maxKits);
        }

        if (kitsToAllocate.signum() <= 0) {
            return created;
        }

        for (KitService.BomComponent component : components) {
            BigDecimal componentQty = kitsToAllocate.multiply(component.quantityPerParent());
            allocateQuantity(line, component.variantId(), componentQty, locationIds, created);
        }

        line.setQtyAllocated(line.getQtyAllocated().add(kitsToAllocate));
        salesOrderLineRepository.save(line);
        return created;
    }

    private void allocateQuantity(SalesOrderLine line, UUID variantId, BigDecimal quantity,
                                  List<UUID> locationIds, List<Allocation> created) {
        BigDecimal remaining = quantity;
        UUID tenantId = TenantContext.requireTenantId();
        List<InventoryLevel> levels = levelRepository.findAvailableForAllocation(tenantId, variantId, locationIds);

        for (InventoryLevel level : levels) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal available = level.getAvailable();
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal qty = available.min(remaining);
            Allocation allocation = new Allocation();
            allocation.setTenantId(tenantId);
            allocation.setSalesOrderLineId(line.getId());
            allocation.setVariantId(variantId);
            allocation.setLocationId(level.getLocationId());
            allocation.setLotId(level.getLotId());
            allocation.setQuantity(qty);
            allocation.setStatus("ACTIVE");
            created.add(allocationRepository.save(allocation));
            remaining = remaining.subtract(qty);
        }
    }

    private void finalizeLineAllocation(SalesOrderLine line, List<Allocation> created) {
        if (!created.isEmpty()) {
            BigDecimal allocated = created.stream().map(Allocation::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            line.setQtyAllocated(line.getQtyAllocated().add(allocated));
            salesOrderLineRepository.save(line);
        }
    }

    @Transactional
    public void releaseForLine(UUID salesOrderLineId) {
        allocationRepository.findBySalesOrderLineIdAndStatus(salesOrderLineId, "ACTIVE").forEach(a -> {
            a.setStatus("RELEASED");
            allocationRepository.save(a);
        });
    }

    /**
     * Device-lock validation for floor pick scans. Throws HTTP 409 Problem Details
     * when the allocation is consumed/cancelled or owned by another picker.
     */
    @Transactional(readOnly = true)
    public Allocation assertPickableForCurrentUser(UUID variantId, UUID allocationId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));

        Allocation allocation;
        if (allocationId != null) {
            allocation = allocationRepository.findByTenantIdAndId(tenantId, allocationId)
                    .orElseThrow(() -> conflict("ALLOCATION_NOT_FOUND", "Allocation not found for this scan",
                            "allocationId", allocationId.toString()));
        } else {
            List<Allocation> active = allocationRepository
                    .findByTenantIdAndVariantIdAndStatus(tenantId, variantId, "ACTIVE");
            if (active.isEmpty()) {
                return null; // blind pick — no SO allocation to lock
            }
            List<Allocation> mine = active.stream()
                    .filter(a -> userId.equals(a.getAssignedToUserId()))
                    .toList();
            if (!mine.isEmpty()) {
                allocation = mine.getFirst();
            } else {
                List<Allocation> lockedByOthers = active.stream()
                        .filter(a -> a.getAssignedToUserId() != null && !userId.equals(a.getAssignedToUserId()))
                        .toList();
                if (!lockedByOthers.isEmpty()) {
                    throw conflict("ALLOCATION_LOCKED",
                            "Task reassigned to another picker",
                            "assignedToUserId", lockedByOthers.getFirst().getAssignedToUserId().toString());
                }
                allocation = active.getFirst();
            }
        }

        String status = allocation.getStatus() == null ? "" : allocation.getStatus().toUpperCase();
        if ("CONSUMED".equals(status)) {
            throw conflict("ALLOCATION_CONSUMED", "Allocation already CONSUMED",
                    "allocationId", allocation.getId().toString());
        }
        if ("CANCELLED".equals(status) || "RELEASED".equals(status)) {
            throw conflict("ALLOCATION_CANCELLED", "Allocation is CANCELLED or RELEASED",
                    "allocationId", allocation.getId().toString(),
                    "status", status);
        }
        if (FulfillmentExceptionService.STATUS_EXCEPTION.equals(status)) {
            throw conflict("ALLOCATION_EXCEPTION", "Allocation shunted as damaged barcode exception",
                    "allocationId", allocation.getId().toString(),
                    "status", status);
        }
        if (!"ACTIVE".equals(status)) {
            throw conflict("ALLOCATION_NOT_ACTIVE", "Allocation is not ACTIVE (" + status + ")",
                    "allocationId", allocation.getId().toString());
        }
        if (allocation.getAssignedToUserId() != null && !userId.equals(allocation.getAssignedToUserId())) {
            throw conflict("ALLOCATION_LOCKED", "Task reassigned to another picker",
                    "assignedToUserId", allocation.getAssignedToUserId().toString());
        }
        if (!variantId.equals(allocation.getVariantId())) {
            throw conflict("ALLOCATION_VARIANT_MISMATCH", "Scan barcode does not match claimed allocation",
                    "allocationId", allocation.getId().toString());
        }
        return allocation;
    }

    @Transactional
    public void consumeForPick(Allocation allocation, BigDecimal qty) {
        if (allocation == null || qty == null || qty.signum() <= 0) {
            return;
        }
        BigDecimal remaining = allocation.getQuantity().subtract(qty);
        if (remaining.signum() <= 0) {
            allocation.setStatus("CONSUMED");
        } else {
            allocation.setQuantity(remaining);
        }
        allocationRepository.save(allocation);
    }

    @Transactional
    public int claimAllocations(List<UUID> allocationIds, UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        int claimed = 0;
        for (UUID id : allocationIds) {
            Allocation allocation = allocationRepository.findByTenantIdAndId(tenantId, id).orElse(null);
            if (allocation == null || !"ACTIVE".equalsIgnoreCase(allocation.getStatus())) {
                continue;
            }
            allocation.setAssignedToUserId(userId);
            allocationRepository.save(allocation);
            claimed++;
        }
        return claimed;
    }

    private static ApiException conflict(String code, String detail, String... kv) {
        ApiException ex = new ApiException(HttpStatus.CONFLICT, code, detail);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            ex.withProperty(kv[i], kv[i + 1]);
        }
        ex.withProperty("reason", detail);
        return ex;
    }
}

package com.invsys.modules.fulfillment.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.inventory.api.AllocationLookup;
import com.invsys.modules.inventory.api.InventoryLevelLookup;
import com.invsys.modules.sales.api.AllocateSalesOrderRequested;
import com.invsys.modules.sales.api.ReleaseSalesOrderAllocationsRequested;
import com.invsys.modules.sales.api.SalesOrderLineLookup;
import com.invsys.modules.sales.api.SalesOrderLookup;
import com.invsys.modules.sales.domain.AllocationPolicy;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.invsys.service.FulfillmentExceptionService;
import com.invsys.service.KitService;

@Service
public class AllocationService {

    private final InventoryLevelLookup levelRepository;
    private final AllocationLookup allocationRepository;
    private final SalesOrderLineLookup salesOrderLineRepository;
    private final SalesOrderLookup salesOrderLookup;
    private final KitService kitService;

    public AllocationService(InventoryLevelLookup levelRepository,
                             AllocationLookup allocationRepository,
                             SalesOrderLineLookup salesOrderLineRepository,
                             SalesOrderLookup salesOrderLookup,
                             KitService kitService) {
        this.levelRepository = levelRepository;
        this.allocationRepository = allocationRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.salesOrderLookup = salesOrderLookup;
        this.kitService = kitService;
    }

    @EventListener
    public void onAllocateRequested(AllocateSalesOrderRequested event) {
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(event.orderId());
        AllocationPolicy policy = salesOrderLookup.findById(event.orderId())
                .map(SalesOrder::getAllocationPolicy)
                .orElse(AllocationPolicy.ALLOW_PARTIAL);
        if (policy == AllocationPolicy.SHIP_COMPLETE && !canFulfillCompletely(lines, event.locationIds())) {
            for (SalesOrderLine line : lines) {
                markBackorderedRemainder(line);
            }
            return;
        }
        for (SalesOrderLine line : lines) {
            allocate(line, event.locationIds());
            SalesOrderLine refreshed = salesOrderLineRepository.findById(line.getId()).orElse(line);
            markBackorderedRemainder(refreshed);
        }
    }

    @EventListener
    public void onReleaseRequested(ReleaseSalesOrderAllocationsRequested event) {
        salesOrderLineRepository.findBySalesOrderId(event.orderId())
                .forEach(line -> releaseForLine(line.getId()));
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
        if (locationIds == null || locationIds.isEmpty()) {
            return created;
        }
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
        if (locationIds == null || locationIds.isEmpty()) {
            return;
        }
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

    private boolean canFulfillCompletely(List<SalesOrderLine> lines, List<UUID> locationIds) {
        for (SalesOrderLine line : lines) {
            BigDecimal remaining = remainingToAllocate(line);
            if (remaining.signum() <= 0) {
                continue;
            }
            if (previewAvailableQty(line, locationIds).compareTo(remaining) < 0) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal previewAvailableQty(SalesOrderLine line, List<UUID> locationIds) {
        if (kitService.isKit(line.getVariantId())) {
            return previewAvailableKits(line, locationIds);
        }
        return availableAtp(line.getVariantId(), locationIds);
    }

    private BigDecimal previewAvailableKits(SalesOrderLine line, List<UUID> locationIds) {
        List<KitService.BomComponent> components = kitService.explodeComponents(line.getVariantId());
        if (components.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal kits = remainingToAllocate(line);
        for (KitService.BomComponent component : components) {
            BigDecimal available = availableAtp(component.variantId(), locationIds);
            BigDecimal maxKits = available.divide(component.quantityPerParent(), 0, RoundingMode.DOWN);
            kits = kits.min(maxKits);
        }
        return kits;
    }

    private BigDecimal availableAtp(UUID variantId, List<UUID> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        UUID tenantId = TenantContext.requireTenantId();
        return levelRepository.findAvailableForAllocation(tenantId, variantId, locationIds).stream()
                .map(InventoryLevel::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal remainingToAllocate(SalesOrderLine line) {
        BigDecimal ordered = line.getQtyOrdered() != null ? line.getQtyOrdered() : BigDecimal.ZERO;
        BigDecimal allocated = line.getQtyAllocated() != null ? line.getQtyAllocated() : BigDecimal.ZERO;
        return ordered.subtract(allocated);
    }

    private void markBackorderedRemainder(SalesOrderLine line) {
        BigDecimal remaining = remainingToAllocate(line);
        line.setQtyBackordered(remaining.signum() > 0 ? remaining : BigDecimal.ZERO);
        salesOrderLineRepository.save(line);
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
                allocation = preferWithOnHand(mine);
            } else {
                List<Allocation> lockedByOthers = active.stream()
                        .filter(a -> a.getAssignedToUserId() != null && !userId.equals(a.getAssignedToUserId()))
                        .toList();
                if (!lockedByOthers.isEmpty()) {
                    throw conflict("ALLOCATION_LOCKED",
                            "Task reassigned to another picker",
                            "assignedToUserId", lockedByOthers.getFirst().getAssignedToUserId().toString());
                }
                allocation = preferWithOnHand(active);
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
        // Flush so inventory_levels.allocated updates before the pick ADJUST validates available.
        allocationRepository.saveAndFlush(allocation);
    }

    /** Prefer an allocation whose bin/lot still has on-hand (stale claimed rows may be empty). */
    private Allocation preferWithOnHand(List<Allocation> candidates) {
        for (Allocation candidate : candidates) {
            if (hasOnHand(candidate)) {
                return candidate;
            }
        }
        return candidates.getFirst();
    }

    private boolean hasOnHand(Allocation allocation) {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal onHand = levelRepository.findByTenantIdAndVariantId(tenantId, allocation.getVariantId())
                .stream()
                .filter(l -> l.getLocationId().equals(allocation.getLocationId()))
                .filter(l -> allocation.getLotId() == null
                        ? l.getLotId() == null
                        : allocation.getLotId().equals(l.getLotId()))
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return onHand.signum() > 0;
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

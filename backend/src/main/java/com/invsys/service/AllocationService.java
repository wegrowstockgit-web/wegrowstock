package com.invsys.service;

import com.invsys.domain.Allocation;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.tenancy.TenantContext;
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
}

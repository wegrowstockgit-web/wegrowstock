package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.inventory.repository.AllocationRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Matches open inbound PO receipts against unfulfilled sales order demand for cross-dock opportunities.
 */
@Service
public class CrossDockService {

    public static final String STATUS_CROSS_DOCK_ROUTED = "CROSS_DOCK_ROUTED";
    public static final String REASON_CROSS_DOCK_ROUTING = "CROSS_DOCK_ROUTING";
    public static final String STAGING_PATH = "WH-01/Z-SHIP/S-01";

    private static final Set<String> OPEN_PO = Set.of("SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED");
    private static final Set<String> OPEN_SO = Set.of("CONFIRMED", "BACKORDERED", "ALLOCATED");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final ProductVariantRepository variantRepository;
    private final AllocationRepository allocationRepository;
    private final LocationRepository locationRepository;
    private final DSLContext dsl;

    public CrossDockService(PurchaseOrderRepository purchaseOrderRepository,
                            PurchaseOrderLineRepository purchaseOrderLineRepository,
                            SalesOrderRepository salesOrderRepository,
                            SalesOrderLineRepository salesOrderLineRepository,
                            ProductVariantRepository variantRepository,
                            AllocationRepository allocationRepository,
                            LocationRepository locationRepository,
                            DSLContext dsl) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.variantRepository = variantRepository;
        this.allocationRepository = allocationRepository;
        this.locationRepository = locationRepository;
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public List<CrossDockSuggestion> suggestions() {
        UUID tenantId = TenantContext.requireTenantId();

        Map<UUID, IncomingDemand> incomingByVariant = new HashMap<>();
        for (PurchaseOrder po : purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (!OPEN_PO.contains(po.getStatus())) {
                continue;
            }
            for (PurchaseOrderLine line : purchaseOrderLineRepository.findByPurchaseOrderId(po.getId())) {
                BigDecimal remaining = line.getQtyOrdered().subtract(
                        line.getQtyReceived() != null ? line.getQtyReceived() : BigDecimal.ZERO);
                if (remaining.signum() <= 0) {
                    continue;
                }
                incomingByVariant.merge(
                        line.getVariantId(),
                        new IncomingDemand(po.getId(), po.getNumber(), line.getId(), remaining),
                        (a, b) -> new IncomingDemand(a.purchaseOrderId(), a.poNumber(), a.poLineId(),
                                a.qty().add(b.qty())));
            }
        }

        List<CrossDockSuggestion> suggestions = new ArrayList<>();
        for (SalesOrder so : salesOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (!OPEN_SO.contains(so.getStatus())) {
                continue;
            }
            for (SalesOrderLine soLine : salesOrderLineRepository.findBySalesOrderId(so.getId())) {
                BigDecimal open = soLine.getQtyOrdered()
                        .subtract(soLine.getQtyShipped() != null ? soLine.getQtyShipped() : BigDecimal.ZERO);
                if (open.signum() <= 0) {
                    continue;
                }
                IncomingDemand inbound = incomingByVariant.get(soLine.getVariantId());
                if (inbound == null || inbound.qty().signum() <= 0) {
                    continue;
                }
                BigDecimal matchQty = open.min(inbound.qty());
                String sku = variantRepository.findById(soLine.getVariantId())
                        .map(v -> v.getSku())
                        .orElse("");
                suggestions.add(new CrossDockSuggestion(
                        soLine.getVariantId(),
                        sku,
                        so.getId(),
                        so.getNumber(),
                        soLine.getId(),
                        inbound.purchaseOrderId(),
                        inbound.poNumber(),
                        inbound.poLineId(),
                        matchQty,
                        open,
                        inbound.qty()));
            }
        }
        return suggestions;
    }

    private record IncomingDemand(UUID purchaseOrderId, String poNumber, UUID poLineId, BigDecimal qty) {
    }

    /**
     * Resolve shipping staging bin (prefers S-01 / Z-SHIP, then any STAGE* location).
     */
    @Transactional(readOnly = true)
    public Location requireStagingLocation() {
        UUID tenantId = TenantContext.requireTenantId();
        Optional<Location> s01 = locationRepository.findByTenantIdAndCode(tenantId, "S-01");
        if (s01.isPresent()) {
            return s01.get();
        }
        return locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .filter(loc -> {
                    String path = loc.getPath() != null ? loc.getPath().toUpperCase() : "";
                    String code = loc.getCode() != null ? loc.getCode().toUpperCase() : "";
                    String name = loc.getName() != null ? loc.getName().toUpperCase() : "";
                    return path.contains("Z-SHIP")
                            || path.contains("STAGE")
                            || code.contains("STAGE")
                            || name.contains("STAGING");
                })
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "STAGING_LOCATION_REQUIRED",
                        "Shipping staging location Z-SHIP/S-01 is not configured"));
    }

    /**
     * Read-only preview: open sales demand for the variant that can intercept inbound receipts.
     */
    @Transactional(readOnly = true)
    public Optional<OpenDemand> previewOpenDemand(UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        Record row = dsl.fetchOne("""
                SELECT sol.id AS sales_order_line_id,
                       sol.sales_order_id,
                       so.number AS so_number,
                       so.status AS so_status,
                       pv.sku,
                       (sol.qty_ordered - COALESCE(sol.qty_shipped, 0)) AS open_qty
                FROM sales_order_lines sol
                JOIN sales_orders so
                  ON so.id = sol.sales_order_id AND so.tenant_id = sol.tenant_id
                JOIN product_variants pv
                  ON pv.id = sol.variant_id AND pv.tenant_id = sol.tenant_id
                WHERE sol.tenant_id = ?
                  AND sol.variant_id = ?
                  AND so.status IN ('CONFIRMED', 'BACKORDERED', 'ALLOCATED', 'PICKING')
                  AND (sol.qty_ordered - COALESCE(sol.qty_shipped, 0)) > 0
                ORDER BY
                  CASE so.status
                    WHEN 'BACKORDERED' THEN 0
                    WHEN 'CONFIRMED' THEN 1
                    ELSE 2
                  END,
                  so.created_at ASC
                LIMIT 1
                """, tenantId, variantId);
        if (row == null) {
            return Optional.empty();
        }
        Location staging = requireStagingLocation();
        return Optional.of(new OpenDemand(
                variantId,
                row.get("sku", String.class),
                row.get("sales_order_id", UUID.class),
                row.get("so_number", String.class),
                row.get("sales_order_line_id", UUID.class),
                row.get("open_qty", BigDecimal.class),
                staging.getId(),
                staging.getPath(),
                staging.getCode()));
    }

    /**
     * High-velocity receive check: open demand (including BACKORDERED) → route to shipping staging.
     * When an ACTIVE allocation exists, marks it CROSS_DOCK_ROUTED.
     */
    @Transactional
    public CrossDockTask checkVariant(UUID variantId) {
        if (variantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "variantId is required");
        }
        UUID tenantId = TenantContext.requireTenantId();

        Record active = dsl.fetchOne("""
                SELECT a.id AS allocation_id,
                       a.quantity,
                       a.sales_order_line_id,
                       sol.sales_order_id,
                       so.number AS so_number,
                       pv.sku
                FROM allocations a
                JOIN sales_order_lines sol
                  ON sol.id = a.sales_order_line_id AND sol.tenant_id = a.tenant_id
                JOIN sales_orders so
                  ON so.id = sol.sales_order_id AND so.tenant_id = a.tenant_id
                JOIN product_variants pv
                  ON pv.id = a.variant_id AND pv.tenant_id = a.tenant_id
                WHERE a.tenant_id = ?
                  AND a.variant_id = ?
                  AND a.status = 'ACTIVE'
                  AND so.status IN ('CONFIRMED', 'BACKORDERED', 'ALLOCATED', 'PICKING')
                ORDER BY a.created_at ASC
                LIMIT 1
                """, tenantId, variantId);

        if (active != null) {
            Location staging = requireStagingLocation();
            UUID allocationId = active.get("allocation_id", UUID.class);
            Allocation allocation = allocationRepository.findByTenantIdAndId(tenantId, allocationId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Allocation not found"));
            allocation.setStatus(STATUS_CROSS_DOCK_ROUTED);
            allocation.setLocationId(staging.getId());
            allocationRepository.save(allocation);

            String sku = active.get("sku", String.class);
            String soNumber = active.get("so_number", String.class);
            BigDecimal qty = active.get("quantity", BigDecimal.class);
            String instruction = stagingInstruction(soNumber, sku, staging.getPath());
            return new CrossDockTask(
                    true,
                    variantId,
                    sku,
                    allocationId,
                    active.get("sales_order_id", UUID.class),
                    soNumber,
                    active.get("sales_order_line_id", UUID.class),
                    staging.getId(),
                    staging.getPath(),
                    qty,
                    STATUS_CROSS_DOCK_ROUTED,
                    instruction);
        }

        return previewOpenDemand(variantId)
                .map(demand -> new CrossDockTask(
                        true,
                        demand.variantId(),
                        demand.sku(),
                        null,
                        demand.salesOrderId(),
                        demand.salesOrderNumber(),
                        demand.salesOrderLineId(),
                        demand.stagingLocationId(),
                        demand.stagingPath(),
                        demand.openQty(),
                        "PENDING_RECEIVE",
                        stagingInstruction(demand.salesOrderNumber(), demand.sku(), demand.stagingPath())))
                .orElseGet(() -> CrossDockTask.none(variantId));
    }

    /**
     * After inbound receipt into staging: soft-allocate the open SO line and flip BACKORDERED → ALLOCATED.
     */
    @Transactional
    public void fulfillOpenDemand(UUID variantId, UUID stagingLocationId, BigDecimal quantity) {
        UUID tenantId = TenantContext.requireTenantId();
        OpenDemand demand = previewOpenDemand(variantId)
                .orElse(null);
        if (demand == null) {
            return;
        }
        BigDecimal qty = quantity.min(demand.openQty());
        if (qty.signum() <= 0) {
            return;
        }

        SalesOrderLine line = salesOrderLineRepository.findById(demand.salesOrderLineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order line not found"));
        Allocation allocation = new Allocation();
        allocation.setTenantId(tenantId);
        allocation.setSalesOrderLineId(line.getId());
        allocation.setVariantId(variantId);
        allocation.setLocationId(stagingLocationId);
        allocation.setQuantity(qty);
        allocation.setStatus(STATUS_CROSS_DOCK_ROUTED);
        allocationRepository.save(allocation);

        BigDecimal allocated = line.getQtyAllocated() != null ? line.getQtyAllocated() : BigDecimal.ZERO;
        line.setQtyAllocated(allocated.add(qty));
        salesOrderLineRepository.save(line);

        SalesOrder order = salesOrderRepository.findById(demand.salesOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        if (List.of("BACKORDERED", "CONFIRMED", "ALLOCATED").contains(order.getStatus())) {
            order.setStatus("ALLOCATED");
            salesOrderRepository.save(order);
        }
    }

    private static String stagingInstruction(String soNumber, String sku, String stagingPath) {
        return "CROSS-DOCK: Route item directly to Shipping Staging Lane "
                + (stagingPath != null ? stagingPath : STAGING_PATH)
                + (soNumber != null ? " for SO " + soNumber : "")
                + (sku != null ? " (" + sku + ")" : "")
                + " — bypass storage put-away";
    }

    public record OpenDemand(
            UUID variantId,
            String sku,
            UUID salesOrderId,
            String salesOrderNumber,
            UUID salesOrderLineId,
            BigDecimal openQty,
            UUID stagingLocationId,
            String stagingPath,
            String stagingCode
    ) {
    }

    public record CrossDockSuggestion(
            UUID variantId,
            String sku,
            UUID salesOrderId,
            String salesOrderNumber,
            UUID salesOrderLineId,
            UUID purchaseOrderId,
            String purchaseOrderNumber,
            UUID purchaseOrderLineId,
            BigDecimal suggestedQty,
            BigDecimal salesOpenQty,
            BigDecimal inboundOpenQty
    ) {
    }

    public record CrossDockTask(
            boolean match,
            UUID variantId,
            String sku,
            UUID allocationId,
            UUID salesOrderId,
            String salesOrderNumber,
            UUID salesOrderLineId,
            UUID stagingHintLocationId,
            String stagingPath,
            BigDecimal quantity,
            String allocationStatus,
            String instruction
    ) {
        static CrossDockTask none(UUID variantId) {
            return new CrossDockTask(false, variantId, null, null, null, null, null, null, null, null, null, null);
        }
    }
}

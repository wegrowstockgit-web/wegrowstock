package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.tenancy.TenantContext;
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
import java.util.Set;
import java.util.UUID;

/**
 * Matches open inbound PO receipts against unfulfilled sales order demand for cross-dock opportunities.
 */
@Service
public class CrossDockService {

    public static final String STATUS_CROSS_DOCK_ROUTED = "CROSS_DOCK_ROUTED";

    private static final Set<String> OPEN_PO = Set.of("SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED");
    private static final Set<String> OPEN_SO = Set.of("CONFIRMED", "ALLOCATED");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final ProductVariantRepository variantRepository;
    private final AllocationRepository allocationRepository;
    private final DSLContext dsl;

    public CrossDockService(PurchaseOrderRepository purchaseOrderRepository,
                            PurchaseOrderLineRepository purchaseOrderLineRepository,
                            SalesOrderRepository salesOrderRepository,
                            SalesOrderLineRepository salesOrderLineRepository,
                            ProductVariantRepository variantRepository,
                            AllocationRepository allocationRepository,
                            DSLContext dsl) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.variantRepository = variantRepository;
        this.allocationRepository = allocationRepository;
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
     * High-velocity receive check: if an ACTIVE sales allocation exists for the variant,
     * route it to shipping staging (bypass put-away / pick-face storage).
     */
    @Transactional
    public CrossDockTask checkVariant(UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (variantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "variantId is required");
        }

        Record row = dsl.fetchOne("""
                SELECT a.id AS allocation_id,
                       a.quantity,
                       a.sales_order_line_id,
                       a.location_id,
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
                  AND so.status IN ('CONFIRMED', 'ALLOCATED', 'PICKING')
                ORDER BY a.created_at ASC
                LIMIT 1
                """, tenantId, variantId);

        if (row == null) {
            return CrossDockTask.none(variantId);
        }

        UUID allocationId = row.get("allocation_id", UUID.class);
        Allocation allocation = allocationRepository.findByTenantIdAndId(tenantId, allocationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Allocation not found"));
        allocation.setStatus(STATUS_CROSS_DOCK_ROUTED);
        allocationRepository.save(allocation);

        String sku = row.get("sku", String.class);
        String soNumber = row.get("so_number", String.class);
        BigDecimal qty = row.get("quantity", BigDecimal.class);
        String instruction = "Route item directly to Shipping Staging Lane"
                + (soNumber != null ? " for SO " + soNumber : "")
                + (sku != null ? " (" + sku + ")" : "");

        return new CrossDockTask(
                true,
                variantId,
                sku,
                allocationId,
                row.get("sales_order_id", UUID.class),
                soNumber,
                row.get("sales_order_line_id", UUID.class),
                row.get("location_id", UUID.class),
                qty,
                STATUS_CROSS_DOCK_ROUTED,
                instruction);
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
            BigDecimal quantity,
            String allocationStatus,
            String instruction
    ) {
        static CrossDockTask none(UUID variantId) {
            return new CrossDockTask(false, variantId, null, null, null, null, null, null, null, null, null);
        }
    }
}

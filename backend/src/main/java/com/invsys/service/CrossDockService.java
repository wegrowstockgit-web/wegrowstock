package com.invsys.service;

import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.tenancy.TenantContext;
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

    private static final Set<String> OPEN_PO = Set.of("SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED");
    private static final Set<String> OPEN_SO = Set.of("CONFIRMED", "ALLOCATED");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final ProductVariantRepository variantRepository;

    public CrossDockService(PurchaseOrderRepository purchaseOrderRepository,
                            PurchaseOrderLineRepository purchaseOrderLineRepository,
                            SalesOrderRepository salesOrderRepository,
                            SalesOrderLineRepository salesOrderLineRepository,
                            ProductVariantRepository variantRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.variantRepository = variantRepository;
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
}

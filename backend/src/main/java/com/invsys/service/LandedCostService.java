package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.common.Money;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.LandedCostAllocation;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.integration.OutboxService;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LandedCostAllocationRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierInvoiceIngestionRepository;
import com.invsys.service.landedcost.HybridLandedCostEngine;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LandedCostService {

    /** @deprecated prefer eventType + strategy names; kept for existing callers/tests */
    public enum AllocationStrategy {
        BY_VALUE,
        BY_WEIGHT,
        BY_VOLUME,
        HYBRID,
        VOLUME,
        WEIGHT,
        QUANTITY,
        VALUE
    }

    private final SupplierInvoiceIngestionRepository ingestionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationRepository locationRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final LandedCostAllocationRepository landedCostAllocationRepository;
    private final InventoryService inventoryService;
    private final CostingService costingService;
    private final OutboxService outboxService;
    private final HybridLandedCostEngine hybridEngine;

    public LandedCostService(SupplierInvoiceIngestionRepository ingestionRepository,
                             PurchaseOrderRepository purchaseOrderRepository,
                             PurchaseOrderLineRepository lineRepository,
                             ProductVariantRepository variantRepository,
                             LocationRepository locationRepository,
                             InventoryLedgerRepository ledgerRepository,
                             LandedCostAllocationRepository landedCostAllocationRepository,
                             InventoryService inventoryService,
                             CostingService costingService,
                             OutboxService outboxService,
                             HybridLandedCostEngine hybridEngine) {
        this.ingestionRepository = ingestionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.ledgerRepository = ledgerRepository;
        this.landedCostAllocationRepository = landedCostAllocationRepository;
        this.inventoryService = inventoryService;
        this.costingService = costingService;
        this.outboxService = outboxService;
        this.hybridEngine = hybridEngine;
    }

    @Transactional
    public LandedCostResult allocate(UUID supplierInvoiceId,
                                     BigDecimal freightTotal,
                                     AllocationStrategy strategy) {
        HybridLandedCostEngine.CostEventType eventType = strategy == AllocationStrategy.BY_VALUE
                || strategy == AllocationStrategy.VALUE
                ? HybridLandedCostEngine.CostEventType.CUSTOMS_DUTY
                : HybridLandedCostEngine.CostEventType.FREIGHT;
        return allocate(supplierInvoiceId, freightTotal, eventType, strategy.name());
    }

    @Transactional
    public LandedCostResult allocate(UUID supplierInvoiceId,
                                     BigDecimal totalCost,
                                     HybridLandedCostEngine.CostEventType eventType,
                                     String strategy) {
        UUID tenantId = TenantContext.requireTenantId();
        if (totalCost == null || totalCost.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FREIGHT", "totalCost must be >= 0");
        }

        String strategyLabel = strategy == null || strategy.isBlank()
                ? (eventType == HybridLandedCostEngine.CostEventType.CUSTOMS_DUTY ? "CUSTOMS" : "HYBRID")
                : strategy.trim().toUpperCase();

        // Fail fast: ValueStrategy is never valid for physical freight (before invoice lookup)
        if (eventType == HybridLandedCostEngine.CostEventType.FREIGHT
                && ("VALUE".equals(strategyLabel) || "BY_VALUE".equals(strategyLabel))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALUE_RESERVED_FOR_CUSTOMS",
                    "ValueStrategy is reserved for Customs/Duties and cannot allocate physical freight");
        }

        SupplierInvoiceIngestion invoice = ingestionRepository.findById(supplierInvoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchasing invoice not found"));
        if (!tenantId.equals(invoice.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchasing invoice not found");
        }

        PurchaseOrder po = purchaseOrderRepository.findById(invoice.getPurchaseOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
        List<PurchaseOrderLine> lines = lineRepository.findByPurchaseOrderId(po.getId()).stream()
                .filter(l -> l.getQtyReceived() != null && l.getQtyReceived().signum() > 0)
                .toList();
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_RECEIPTS",
                    "Landed cost requires at least one received PO line");
        }

        Map<UUID, Money> shares = hybridEngine.allocateWithStrategy(
                Money.of(totalCost), lines, strategyLabel, eventType);

        List<Map<String, Object>> breakdown = new ArrayList<>();
        List<UUID> ledgerIds = new ArrayList<>();

        for (PurchaseOrderLine line : lines) {
            Money shareMoney = shares.getOrDefault(line.getId(), Money.ZERO);
            BigDecimal share = shareMoney.toBigDecimal();
            BigDecimal qty = line.getQtyReceived();
            BigDecimal perUnit = qty.signum() > 0
                    ? share.divide(qty, 4, RoundingMode.HALF_UP)
                    : share;

            ProductVariant variant = variantRepository.findById(line.getVariantId()).orElseThrow();
            UUID locationId = resolveReceiveLocation(tenantId, line.getId());
            InventoryLedger entry = inventoryService.appendCostAdjustment(
                    variant.getId(),
                    locationId,
                    perUnit,
                    "LANDED_COST_ALLOCATION",
                    "SUPPLIER_INVOICE",
                    invoice.getId(),
                    share);
            ledgerIds.add(entry.getId());
            costingService.applyLandedCostAmount(variant.getId(), share);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("poLineId", line.getId().toString());
            row.put("variantId", variant.getId().toString());
            row.put("sku", variant.getSku());
            row.put("qtyReceived", qty);
            row.put("allocatedFreight", share);
            row.put("perUnitLanded", perUnit);
            row.put("ledgerId", entry.getId().toString());
            breakdown.add(row);
        }

        String auditStrategy = eventType == HybridLandedCostEngine.CostEventType.CUSTOMS_DUTY
                ? "CUSTOMS"
                : strategyLabel;

        LandedCostAllocation audit = new LandedCostAllocation();
        audit.setTenantId(tenantId);
        audit.setSupplierInvoiceId(invoice.getId());
        audit.setPurchaseOrderId(po.getId());
        audit.setFreightTotal(totalCost);
        audit.setStrategy(auditStrategy);
        audit.setLineBreakdown(breakdown);
        audit = landedCostAllocationRepository.save(audit);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allocationId", audit.getId().toString());
        payload.put("invoiceId", invoice.getId().toString());
        payload.put("freightTotal", totalCost);
        payload.put("eventType", eventType.name());
        payload.put("strategy", auditStrategy);
        payload.put("ledgerIds", ledgerIds.stream().map(UUID::toString).toList());
        outboxService.append("LANDED_COST", audit.getId(), "LANDED_COST_ALLOCATED", payload);

        return new LandedCostResult(audit.getId(), invoice.getId(), po.getId(), totalCost, auditStrategy, breakdown);
    }

    private UUID resolveReceiveLocation(UUID tenantId, UUID poLineId) {
        return ledgerRepository
                .findByTenantIdAndReferenceTypeAndReferenceId(tenantId, "PURCHASE_ORDER_LINE", poLineId)
                .stream()
                .filter(e -> "RECEIVE".equals(e.getMovementType()))
                .map(InventoryLedger::getLocationId)
                .findFirst()
                .orElseGet(() -> locationRepository.findByTenantIdAndType(tenantId, "WAREHOUSE").stream()
                        .map(Location::getId)
                        .findFirst()
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_LOCATION",
                                "No warehouse location for landed cost ledger")));
    }

    public record LandedCostResult(
            UUID allocationId,
            UUID invoiceId,
            UUID purchaseOrderId,
            BigDecimal freightTotal,
            String strategy,
            List<Map<String, Object>> lines
    ) {
    }
}

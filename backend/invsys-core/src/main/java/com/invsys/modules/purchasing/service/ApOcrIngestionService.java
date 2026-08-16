package com.invsys.modules.purchasing.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.ApMatchingLog;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.core.integration.OutboxService;
import com.invsys.repository.ApMatchingLogRepository;
import com.invsys.modules.inventory.api.InventoryLedgerLookup;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierInvoiceIngestionRepository;
import com.invsys.core.tenancy.TenantContext;
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
import com.invsys.modules.sales.domain.Invoice;

/**
 * Supplier document ingestion with defensive three-way matching:
 * PO ordered qty × verified RECEIVE ledger / qty_received × AP invoice costs.
 */
@Service
public class ApOcrIngestionService {

    private static final BigDecimal QTY_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal PRICE_TOLERANCE_PCT = new BigDecimal("5.00");

    private final SupplierInvoiceIngestionRepository ingestionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLedgerLookup inventoryLedgerRepository;
    private final ApMatchingLogRepository apMatchingLogRepository;
    private final OutboxService outboxService;

    public ApOcrIngestionService(SupplierInvoiceIngestionRepository ingestionRepository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 PurchaseOrderLineRepository lineRepository,
                                 ProductVariantRepository variantRepository,
                                 InventoryLedgerLookup inventoryLedgerRepository,
                                 ApMatchingLogRepository apMatchingLogRepository,
                                 OutboxService outboxService) {
        this.ingestionRepository = ingestionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.variantRepository = variantRepository;
        this.inventoryLedgerRepository = inventoryLedgerRepository;
        this.apMatchingLogRepository = apMatchingLogRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public SupplierInvoiceIngestion submitDocument(UUID purchaseOrderId, Map<String, Object> extractedData) {
        return submitDocument(purchaseOrderId, extractedData, null);
    }

    @Transactional
    public SupplierInvoiceIngestion submitDocument(UUID purchaseOrderId,
                                                   Map<String, Object> extractedData,
                                                   String documentUrl) {
        UUID tenantId = TenantContext.requireTenantId();
        purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));

        SupplierInvoiceIngestion ingestion = new SupplierInvoiceIngestion();
        ingestion.setTenantId(tenantId);
        ingestion.setPurchaseOrderId(purchaseOrderId);
        ingestion.setStatus("PENDING");
        ingestion.setDocumentUrl(documentUrl);
        ingestion.setExtractedData(extractedData != null ? new LinkedHashMap<>(extractedData) : new LinkedHashMap<>());
        ingestion = ingestionRepository.save(ingestion);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ingestionId", ingestion.getId().toString());
        payload.put("purchaseOrderId", purchaseOrderId.toString());
        payload.put("documentUrl", documentUrl);
        payload.put("extractedData", ingestion.getExtractedData());
        outboxService.append("SUPPLIER_INVOICE", ingestion.getId(), "SUPPLIER_DOCUMENT_UPLOADED", payload);

        return ingestion;
    }

    /**
     * Three-way match: (1) PO ordered vs invoice lines, (2) verified RECEIVE, (3) cost boundaries.
     * Writes {@code ap_matching_logs}. Never marks the PO RECEIVED from invoice alone.
     */
    @Transactional
    public SupplierInvoiceIngestion reconcile(UUID ingestionId) {
        UUID tenantId = TenantContext.requireTenantId();
        SupplierInvoiceIngestion ingestion = ingestionRepository.findById(ingestionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ingestion not found"));

        PurchaseOrder po = purchaseOrderRepository.findById(ingestion.getPurchaseOrderId()).orElseThrow();
        List<PurchaseOrderLine> poLines = lineRepository.findByPurchaseOrderId(po.getId());

        ThreeWayResult result = compareThreeWay(ingestion.getExtractedData(), poLines, tenantId);
        ingestion.setMatchConfidence(result.confidence());
        ingestion.setExtractedData(enrichWithConflicts(ingestion.getExtractedData(), result.conflicts()));

        if (result.conflicts().isEmpty()) {
            ingestion.setStatus("RECONCILED");
        } else {
            ingestion.setStatus("CONFLICT");
        }
        ingestion = ingestionRepository.save(ingestion);

        ApMatchingLog log = new ApMatchingLog();
        log.setTenantId(tenantId);
        log.setIngestionId(ingestion.getId());
        log.setPoId(po.getId());
        log.setMatchStatus(result.matchStatus());
        log.setValidationErrors(result.validationErrors());
        apMatchingLogRepository.save(log);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ingestionId", ingestion.getId().toString());
        payload.put("poId", po.getId().toString());
        payload.put("matchStatus", result.matchStatus());
        payload.put("conflictCount", result.conflicts().size());
        outboxService.append("SUPPLIER_INVOICE", ingestion.getId(), "AP_THREE_WAY_MATCH", payload);

        return ingestion;
    }

    public List<SupplierInvoiceIngestion> listForTenant() {
        return ingestionRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }

    public List<SupplierInvoiceIngestion> listForPurchaseOrder(UUID purchaseOrderId) {
        return ingestionRepository.findByTenantIdAndPurchaseOrderIdOrderByCreatedAtDesc(
                TenantContext.requireTenantId(), purchaseOrderId);
    }

    @SuppressWarnings("unchecked")
    private ThreeWayResult compareThreeWay(Map<String, Object> extracted,
                                           List<PurchaseOrderLine> poLines,
                                           UUID tenantId) {
        List<Map<String, Object>> extractedLines = extracted.get("lines") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();

        List<LineConflict> conflicts = new ArrayList<>();
        List<Map<String, Object>> validationErrors = new ArrayList<>();
        int matched = 0;
        int total = poLines.size();
        boolean qtyMismatch = false;
        boolean costMismatch = false;
        boolean receiptMismatch = false;

        boolean anyPhysicalReceipt = poLines.stream()
                .anyMatch(l -> l.getQtyReceived() != null && l.getQtyReceived().signum() > 0);
        if (!anyPhysicalReceipt) {
            receiptMismatch = true;
            conflicts.add(new LineConflict("*", "NO_RECEIVE", null, null, null, null));
            validationErrors.add(Map.of(
                    "code", "NO_RECEIVE",
                    "message", "No verified RECEIVE quantity for PO — three-way match blocked"));
        }

        for (PurchaseOrderLine poLine : poLines) {
            String sku = variantRepository.findById(poLine.getVariantId()).map(v -> v.getSku()).orElse("");
            Map<String, Object> extractedLine = extractedLines.stream()
                    .filter(l -> sku.equals(String.valueOf(l.getOrDefault("sku", ""))))
                    .findFirst()
                    .orElse(null);

            if (extractedLine == null) {
                qtyMismatch = true;
                conflicts.add(new LineConflict(sku, "MISSING_LINE", poLine.getQtyOrdered(), null, poLine.getUnitCost(), null));
                validationErrors.add(Map.of("code", "MISSING_LINE", "sku", sku, "message", "Invoice missing PO line"));
                continue;
            }

            BigDecimal extQty = toDecimal(extractedLine.get("qty"));
            BigDecimal extCost = toDecimal(extractedLine.get("unitCost"));
            BigDecimal receivedQty = poLine.getQtyReceived() != null ? poLine.getQtyReceived() : BigDecimal.ZERO;
            BigDecimal ledgerReceived = sumLedgerReceive(tenantId, poLine.getId());

            boolean qtyOk = extQty.subtract(poLine.getQtyOrdered()).abs().compareTo(QTY_TOLERANCE) <= 0;
            boolean costOk = poLine.getUnitCost() == null
                    || poLine.getUnitCost().signum() == 0
                    || withinPriceBoundary(extCost, poLine.getUnitCost());
            boolean receiveOk = receivedQty.subtract(poLine.getQtyOrdered()).abs().compareTo(QTY_TOLERANCE) <= 0
                    && ledgerReceived.subtract(poLine.getQtyOrdered()).abs().compareTo(QTY_TOLERANCE) <= 0;

            if (qtyOk && costOk && receiveOk) {
                matched++;
            } else {
                if (!qtyOk) {
                    qtyMismatch = true;
                    conflicts.add(new LineConflict(sku, "QTY_MISMATCH", poLine.getQtyOrdered(), extQty, null, null));
                    validationErrors.add(Map.of(
                            "code", "QTY_PO_INVOICE",
                            "sku", sku,
                            "poOrdered", poLine.getQtyOrdered(),
                            "invoiceQty", extQty));
                }
                if (!costOk) {
                    costMismatch = true;
                    conflicts.add(new LineConflict(sku, "COST_MISMATCH", null, null, poLine.getUnitCost(), extCost));
                    validationErrors.add(Map.of(
                            "code", "PRICE_BOUNDARY",
                            "sku", sku,
                            "poUnitCost", poLine.getUnitCost(),
                            "invoiceUnitCost", extCost));
                }
                if (!receiveOk) {
                    receiptMismatch = true;
                    conflicts.add(new LineConflict(sku, "RECEIPT_MISMATCH", poLine.getQtyOrdered(), receivedQty, null, null));
                    validationErrors.add(Map.of(
                            "code", "QTY_RECEIVE",
                            "sku", sku,
                            "poOrdered", poLine.getQtyOrdered(),
                            "qtyReceived", receivedQty,
                            "ledgerReceive", ledgerReceived));
                }
            }
        }

        BigDecimal confidence = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(matched).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

        String matchStatus = resolveMatchStatus(conflicts.isEmpty(), qtyMismatch, costMismatch, receiptMismatch);
        return new ThreeWayResult(confidence, conflicts, validationErrors, matchStatus);
    }

    private static String resolveMatchStatus(boolean clean,
                                             boolean qtyMismatch,
                                             boolean costMismatch,
                                             boolean receiptMismatch) {
        if (clean) {
            return "MATCHED";
        }
        int flags = (qtyMismatch ? 1 : 0) + (costMismatch ? 1 : 0) + (receiptMismatch ? 1 : 0);
        if (flags > 1) {
            return "PARTIAL";
        }
        if (receiptMismatch) {
            return "RECEIPT_MISMATCH";
        }
        if (qtyMismatch) {
            return "QTY_MISMATCH";
        }
        if (costMismatch) {
            return "COST_MISMATCH";
        }
        return "FAILED";
    }

    private boolean withinPriceBoundary(BigDecimal invoiceCost, BigDecimal poCost) {
        if (invoiceCost.compareTo(poCost) == 0) {
            return true;
        }
        BigDecimal pct = invoiceCost.subtract(poCost).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(poCost, 4, RoundingMode.HALF_UP);
        return pct.compareTo(PRICE_TOLERANCE_PCT) <= 0;
    }

    private BigDecimal sumLedgerReceive(UUID tenantId, UUID purchaseOrderLineId) {
        return inventoryLedgerRepository
                .findByTenantIdAndReferenceTypeAndReferenceId(tenantId, "PURCHASE_ORDER_LINE", purchaseOrderLineId)
                .stream()
                .filter(e -> "RECEIVE".equals(e.getMovementType()))
                .map(InventoryLedger::getQuantityDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();
    }

    private Map<String, Object> enrichWithConflicts(Map<String, Object> data, List<LineConflict> conflicts) {
        Map<String, Object> enriched = new LinkedHashMap<>(data);
        enriched.put("conflicts", conflicts.stream().map(c -> Map.of(
                "sku", c.sku(),
                "type", c.type(),
                "expectedQty", c.expectedQty() != null ? c.expectedQty() : "",
                "actualQty", c.actualQty() != null ? c.actualQty() : "",
                "expectedCost", c.expectedCost() != null ? c.expectedCost() : "",
                "actualCost", c.actualCost() != null ? c.actualCost() : ""
        )).toList());
        return enriched;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private record ThreeWayResult(
            BigDecimal confidence,
            List<LineConflict> conflicts,
            List<Map<String, Object>> validationErrors,
            String matchStatus
    ) {
    }

    public record LineConflict(
            String sku,
            String type,
            BigDecimal expectedQty,
            BigDecimal actualQty,
            BigDecimal expectedCost,
            BigDecimal actualCost
    ) {
    }
}

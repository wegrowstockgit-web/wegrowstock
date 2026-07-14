package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.integration.OutboxService;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierInvoiceIngestionRepository;
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
public class ApOcrIngestionService {

    private final SupplierInvoiceIngestionRepository ingestionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final ProductVariantRepository variantRepository;
    private final OutboxService outboxService;

    public ApOcrIngestionService(SupplierInvoiceIngestionRepository ingestionRepository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 PurchaseOrderLineRepository lineRepository,
                                 ProductVariantRepository variantRepository,
                                 OutboxService outboxService) {
        this.ingestionRepository = ingestionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.variantRepository = variantRepository;
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

    @Transactional
    public SupplierInvoiceIngestion reconcile(UUID ingestionId) {
        UUID tenantId = TenantContext.requireTenantId();
        SupplierInvoiceIngestion ingestion = ingestionRepository.findById(ingestionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ingestion not found"));

        PurchaseOrder po = purchaseOrderRepository.findById(ingestion.getPurchaseOrderId()).orElseThrow();
        List<PurchaseOrderLine> poLines = lineRepository.findByPurchaseOrderId(po.getId());

        ReconciliationResult result = compareExtractedToPo(ingestion.getExtractedData(), poLines);
        ingestion.setMatchConfidence(result.confidence());
        ingestion.setExtractedData(enrichWithConflicts(ingestion.getExtractedData(), result.conflicts()));

        if (result.conflicts().isEmpty()) {
            ingestion.setStatus("RECONCILED");
            if ("SUBMITTED".equals(po.getStatus())) {
                po.setStatus("RECEIVED");
                purchaseOrderRepository.save(po);
            }
        } else {
            ingestion.setStatus("CONFLICT");
        }
        return ingestionRepository.save(ingestion);
    }

    public List<SupplierInvoiceIngestion> listForTenant() {
        return ingestionRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }

    public List<SupplierInvoiceIngestion> listForPurchaseOrder(UUID purchaseOrderId) {
        return ingestionRepository.findByTenantIdAndPurchaseOrderIdOrderByCreatedAtDesc(
                TenantContext.requireTenantId(), purchaseOrderId);
    }

    @SuppressWarnings("unchecked")
    private ReconciliationResult compareExtractedToPo(Map<String, Object> extracted, List<PurchaseOrderLine> poLines) {
        List<Map<String, Object>> extractedLines = extracted.get("lines") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();

        List<LineConflict> conflicts = new ArrayList<>();
        int matched = 0;
        int total = poLines.size();

        for (PurchaseOrderLine poLine : poLines) {
            String sku = variantRepository.findById(poLine.getVariantId()).map(v -> v.getSku()).orElse("");
            Map<String, Object> extractedLine = extractedLines.stream()
                    .filter(l -> sku.equals(String.valueOf(l.getOrDefault("sku", ""))))
                    .findFirst()
                    .orElse(null);

            if (extractedLine == null) {
                conflicts.add(new LineConflict(sku, "MISSING_LINE", poLine.getQtyOrdered(), null, poLine.getUnitCost(), null));
                continue;
            }

            BigDecimal extQty = toDecimal(extractedLine.get("qty"));
            BigDecimal extCost = toDecimal(extractedLine.get("unitCost"));

            boolean qtyOk = extQty.compareTo(poLine.getQtyOrdered()) == 0;
            boolean costOk = poLine.getUnitCost() == null
                    || poLine.getUnitCost().signum() == 0
                    || extCost.compareTo(poLine.getUnitCost()) == 0;

            if (qtyOk && costOk) {
                matched++;
            } else {
                if (!qtyOk) {
                    conflicts.add(new LineConflict(sku, "QTY_MISMATCH", poLine.getQtyOrdered(), extQty, null, null));
                }
                if (!costOk) {
                    conflicts.add(new LineConflict(sku, "COST_MISMATCH", null, null, poLine.getUnitCost(), extCost));
                }
            }
        }

        BigDecimal confidence = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(matched).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

        return new ReconciliationResult(confidence, conflicts);
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

    private record ReconciliationResult(BigDecimal confidence, List<LineConflict> conflicts) {
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

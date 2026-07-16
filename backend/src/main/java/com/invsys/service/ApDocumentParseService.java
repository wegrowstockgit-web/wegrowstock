package com.invsys.service;

import com.invsys.domain.ApInvoiceIngestion;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.media.ObjectStorage;
import com.invsys.repository.ApInvoiceIngestionRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic document metadata extraction for AP invoice uploads.
 * Parses supplier names, SKUs, costs, and quantities from text/CSV-like payloads
 * and matches open purchase-order lines via jOOQ.
 */
@Service
public class ApDocumentParseService {

    private static final Logger log = LoggerFactory.getLogger(ApDocumentParseService.class);

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "(?i)(?:sku[:\\s]*)?([A-Z0-9][A-Z0-9._-]{2,40})\\s+.*?(\\d+(?:\\.\\d+)?)\\s+(?:@\\s*)?\\$?(\\d+(?:\\.\\d{1,4})?)");

    private final ApInvoiceIngestionRepository ingestionRepository;
    private final ObjectStorage objectStorage;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SupplierRepository supplierRepository;
    private final DSLContext dsl;

    public ApDocumentParseService(ApInvoiceIngestionRepository ingestionRepository,
                                  ObjectStorage objectStorage,
                                  PurchaseOrderLineRepository purchaseOrderLineRepository,
                                  ProductVariantRepository productVariantRepository,
                                  SupplierRepository supplierRepository,
                                  DSLContext dsl) {
        this.ingestionRepository = ingestionRepository;
        this.objectStorage = objectStorage;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.productVariantRepository = productVariantRepository;
        this.supplierRepository = supplierRepository;
        this.dsl = dsl;
    }

    @Transactional
    public void processIngestion(UUID tenantId, UUID ingestionId) {
        TenantContext.setTenantId(tenantId);
        try {
            ApInvoiceIngestion ingestion = ingestionRepository.findById(ingestionId).orElse(null);
            if (ingestion == null || !tenantId.equals(ingestion.getTenantId())) {
                return;
            }
            String text = readDocumentText(ingestion.getFileStorageKey());
            Map<String, Object> metadata = extractMetadata(tenantId, text);
            UUID matchedPo = matchOpenPurchaseOrder(tenantId, metadata).orElse(null);
            metadata.put("matchedPurchaseOrderId", matchedPo != null ? matchedPo.toString() : null);

            ingestion.setParsedMetadata(metadata);
            ingestion.setMatchedPurchaseOrderId(matchedPo);
            ingestion.setIngestionStatus(matchedPo != null || hasLines(metadata) ? "STAGED" : "FAILED");
            if (matchedPo == null && !hasLines(metadata)) {
                metadata.put("error", "Unable to extract invoice lines or match an open purchase order");
                ingestion.setParsedMetadata(metadata);
            }
            ingestionRepository.save(ingestion);
        } catch (Exception ex) {
            log.warn("AP document parse failed for {}: {}", ingestionId, ex.getMessage());
            ingestionRepository.findById(ingestionId).ifPresent(row -> {
                if (tenantId.equals(row.getTenantId())) {
                    Map<String, Object> meta = new LinkedHashMap<>(row.getParsedMetadata());
                    meta.put("error", ex.getMessage() != null ? ex.getMessage() : "Parse failed");
                    row.setParsedMetadata(meta);
                    row.setIngestionStatus("FAILED");
                    ingestionRepository.save(row);
                }
            });
        } finally {
            TenantContext.clear();
        }
    }

    private String readDocumentText(String storageKey) {
        try (InputStream in = objectStorage.open(storageKey)) {
            byte[] bytes = in.readAllBytes();
            String asText = new String(bytes, StandardCharsets.UTF_8);
            // Prefer printable text; binary PDFs still often contain embedded ASCII SKUs.
            StringBuilder printable = new StringBuilder(asText.length());
            for (int i = 0; i < asText.length(); i++) {
                char c = asText.charAt(i);
                if (c == '\n' || c == '\r' || c == '\t' || (c >= 32 && c < 127)) {
                    printable.append(c);
                } else {
                    printable.append(' ');
                }
            }
            return printable.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read uploaded document", ex);
        }
    }

    Map<String, Object> extractMetadata(UUID tenantId, String text) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("supplierName", detectSupplierName(tenantId, text));
        List<Map<String, Object>> lines = new ArrayList<>();
        Matcher matcher = LINE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sku = matcher.group(1).toUpperCase(Locale.ROOT);
            if (!looksLikeSku(sku)) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sku", sku);
            line.put("qty", new BigDecimal(matcher.group(2)));
            line.put("unitCost", new BigDecimal(matcher.group(3)));
            lines.add(line);
        }
        // CSV-style fallback: sku,qty,unitCost
        if (lines.isEmpty()) {
            for (String raw : text.split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.toLowerCase(Locale.ROOT).startsWith("sku")) {
                    continue;
                }
                String[] parts = line.split("[,;\\t]+");
                if (parts.length < 3) {
                    continue;
                }
                try {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sku", parts[0].trim().toUpperCase(Locale.ROOT));
                    row.put("qty", new BigDecimal(parts[1].trim()));
                    row.put("unitCost", new BigDecimal(parts[2].trim().replace("$", "")));
                    if (looksLikeSku((String) row.get("sku"))) {
                        lines.add(row);
                    }
                } catch (NumberFormatException ignored) {
                    // skip non-numeric rows
                }
            }
        }
        metadata.put("lines", lines);
        return metadata;
    }

    private String detectSupplierName(UUID tenantId, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (Supplier supplier : supplierRepository.findByTenantIdOrderByNameAsc(tenantId)) {
            if (supplier.getName() != null && lower.contains(supplier.getName().toLowerCase(Locale.ROOT))) {
                return supplier.getName();
            }
        }
        Matcher m = Pattern.compile("(?i)supplier[:\\s]+([A-Za-z0-9 .,&-]{3,80})").matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private Optional<UUID> matchOpenPurchaseOrder(UUID tenantId, Map<String, Object> metadata) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) metadata.getOrDefault("lines", List.of());
        if (lines.isEmpty()) {
            return Optional.empty();
        }

        Result<Record> openPos = dsl.fetch("""
                SELECT po.id AS po_id, po.supplier_id AS supplier_id, s.name AS supplier_name
                FROM purchase_orders po
                LEFT JOIN suppliers s ON s.id = po.supplier_id AND s.tenant_id = po.tenant_id
                WHERE po.tenant_id = ?
                  AND po.status IN ('DRAFT', 'SUBMITTED', 'CONFIRMED', 'PARTIALLY_RECEIVED')
                ORDER BY po.created_at DESC
                """, tenantId);

        String supplierName = metadata.get("supplierName") instanceof String s ? s : null;
        UUID bestPo = null;
        int bestScore = -1;

        for (Record poRow : openPos) {
            UUID poId = poRow.get("po_id", UUID.class);
            String poSupplier = poRow.get("supplier_name", String.class);
            List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrderId(poId);
            int score = 0;
            if (supplierName != null && poSupplier != null
                    && poSupplier.equalsIgnoreCase(supplierName)) {
                score += 5;
            }
            for (Map<String, Object> line : lines) {
                String sku = String.valueOf(line.get("sku"));
                Optional<ProductVariant> variant = productVariantRepository
                        .findByTenantIdAndSku(tenantId, sku);
                if (variant.isEmpty()) {
                    continue;
                }
                UUID variantId = variant.get().getId();
                boolean matched = poLines.stream().anyMatch(pl -> variantId.equals(pl.getVariantId()));
                if (matched) {
                    score += 3;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestPo = poId;
            }
        }
        return bestScore > 0 ? Optional.ofNullable(bestPo) : Optional.empty();
    }

    private static boolean hasLines(Map<String, Object> metadata) {
        Object lines = metadata.get("lines");
        return lines instanceof List<?> list && !list.isEmpty();
    }

    private static boolean looksLikeSku(String sku) {
        if (sku == null || sku.length() < 3) {
            return false;
        }
        String upper = sku.toUpperCase(Locale.ROOT);
        return !List.of("THE", "AND", "FOR", "TOTAL", "INVOICE", "QTY", "QUANTITY", "PRICE").contains(upper);
    }
}

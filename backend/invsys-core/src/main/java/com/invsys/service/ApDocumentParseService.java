package com.invsys.service;

import com.invsys.modules.purchasing.domain.ApInvoiceIngestion;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.media.ObjectStorage;
import com.invsys.modules.purchasing.repository.ApInvoiceIngestionRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.core.tenancy.TenantContext;
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

    private static final int MAX_PARSE_CHARS = 200_000;
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "(?i)(?:sku[:\\s]*)?([A-Z0-9][A-Z0-9._-]{2,40})\\s+(\\d+(?:\\.\\d+)?)\\s+(?:@\\s*)?\\$?(\\d+(?:\\.\\d{1,4})?)");
    private static final Pattern INVOICE_NUMBER = Pattern.compile(
            "(?i)invoice\\s*(?:number|no\\.?|#)\\s*[:#]?\\s*([A-Z0-9][A-Z0-9._/-]{1,40})");
    private static final Pattern INVOICE_DATE = Pattern.compile(
            "(?i)invoice\\s*date\\s*[:#]?\\s*(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})");
    private static final Pattern PO_NUMBER = Pattern.compile(
            "(?i)(?:(?:purchase\\s*order|po(?:\\s+number|\\s+no\\.?)?)\\s*[:#]?\\s*)?(PO[-][A-Z0-9][-A-Z0-9]*)");
    private static final Pattern SUBTOTAL = Pattern.compile(
            "(?i)subtotal\\s*[:$]?\\s*\\$?(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern TAX = Pattern.compile(
            "(?i)(?:tax|vat|gst)\\s*[:$]?\\s*\\$?(\\d+(?:\\.\\d{1,2})?)");

    private final ApInvoiceIngestionRepository ingestionRepository;
    private final ObjectStorage objectStorage;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SupplierRepository supplierRepository;
    private final DSLContext dsl;

    public ApDocumentParseService(ApInvoiceIngestionRepository ingestionRepository,
                                  ObjectStorage objectStorage,
                                  PurchaseOrderLineRepository purchaseOrderLineRepository,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  ProductVariantRepository productVariantRepository,
                                  SupplierRepository supplierRepository,
                                  DSLContext dsl) {
        this.ingestionRepository = ingestionRepository;
        this.objectStorage = objectStorage;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
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
            byte[] bytes = in.readNBytes(MAX_PARSE_CHARS);
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

    public Map<String, Object> extractFromBytes(UUID tenantId, byte[] bytes) {
        String asText = new String(bytes, StandardCharsets.UTF_8);
        StringBuilder printable = new StringBuilder(asText.length());
        for (int i = 0; i < asText.length(); i++) {
            char c = asText.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 32 && c < 127)) {
                printable.append(c);
            } else {
                printable.append(' ');
            }
        }
        return extractMetadata(tenantId, printable.toString());
    }

    public Optional<UUID> detectPurchaseOrder(UUID tenantId, Map<String, Object> metadata) {
        Object detected = metadata.get("detectedPoNumber");
        if (detected instanceof String poNumber && !poNumber.isBlank()) {
            Optional<UUID> byNumber = purchaseOrderRepository
                    .findByTenantIdAndNumberIgnoreCase(tenantId, poNumber.trim())
                    .map(po -> po.getId());
            if (byNumber.isPresent()) {
                return byNumber;
            }
        }
        return matchOpenPurchaseOrder(tenantId, metadata);
    }

    Map<String, Object> extractMetadata(UUID tenantId, String text) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("supplierName", detectSupplierName(tenantId, text));
        metadata.put("invoiceNumber", firstGroup(INVOICE_NUMBER, text));
        metadata.put("invoiceDate", firstGroup(INVOICE_DATE, text));
        metadata.put("detectedPoNumber", firstGroup(PO_NUMBER, text));
        metadata.put("subtotal", firstDecimal(SUBTOTAL, text));
        metadata.put("tax", firstDecimal(TAX, text));
        List<Map<String, Object>> lines = new ArrayList<>();
        String bounded = text.length() > MAX_PARSE_CHARS ? text.substring(0, MAX_PARSE_CHARS) : text;
        Matcher matcher = LINE_PATTERN.matcher(bounded);
        while (matcher.find()) {
            String sku = matcher.group(1).toUpperCase(Locale.ROOT);
            if (!looksLikeSku(sku)) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sku", sku);
            line.put("qty", new BigDecimal(matcher.group(2)));
            line.put("unitCost", new BigDecimal(matcher.group(3)));
            line.put("confidence", new BigDecimal("0.92"));
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
                    row.put("confidence", new BigDecimal("0.70"));
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
                  AND po.status IN ('DRAFT', 'SUBMITTED', 'CONFIRMED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED', 'RECEIVED')
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

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static BigDecimal firstDecimal(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return new BigDecimal(m.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean looksLikeSku(String sku) {
        if (sku == null || sku.length() < 3) {
            return false;
        }
        String upper = sku.toUpperCase(Locale.ROOT);
        return !List.of("THE", "AND", "FOR", "TOTAL", "INVOICE", "QTY", "QUANTITY", "PRICE").contains(upper);
    }
}

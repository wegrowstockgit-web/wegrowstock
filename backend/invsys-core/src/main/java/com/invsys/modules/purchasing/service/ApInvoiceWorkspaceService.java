package com.invsys.modules.purchasing.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.service.CycleCountService;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierInvoiceIngestionRepository;
import com.invsys.service.ApDocumentParseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structured AP Document Workspace: file ingest → header/OCR lines → 3-way match → resolve.
 */
@Service
public class ApInvoiceWorkspaceService {

    private static final BigDecimal QTY_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal PRICE_TOLERANCE_PCT = new BigDecimal("5.00");
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.80");

    private final ApDocumentParseService parseService;
    private final ApOcrIngestionService ocrIngestionService;
    private final SupplierInvoiceIngestionRepository ingestionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final CycleCountService cycleCountService;

    public ApInvoiceWorkspaceService(ApDocumentParseService parseService,
                                     ApOcrIngestionService ocrIngestionService,
                                     SupplierInvoiceIngestionRepository ingestionRepository,
                                     PurchaseOrderRepository purchaseOrderRepository,
                                     PurchaseOrderLineRepository lineRepository,
                                     ProductVariantRepository variantRepository,
                                     ProductRepository productRepository,
                                     LocationRepository locationRepository,
                                     CycleCountService cycleCountService) {
        this.parseService = parseService;
        this.ocrIngestionService = ocrIngestionService;
        this.ingestionRepository = ingestionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.cycleCountService = cycleCountService;
    }

    @Transactional
    public ApWorkspaceResponse ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Drop a PDF or image to ingest");
        }
        UUID tenantId = TenantContext.requireTenantId();
        Map<String, Object> extracted;
        try {
            extracted = parseService.extractFromBytes(tenantId, file.getBytes());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PARSE_FAILED",
                    ex.getMessage() != null ? ex.getMessage() : "Unable to read invoice");
        }
        UUID poId = parseService.detectPurchaseOrder(tenantId, extracted).orElse(null);
        if (poId != null) {
            extracted.put("matchedPurchaseOrderId", poId.toString());
            SupplierInvoiceIngestion saved = ocrIngestionService.submitDocument(poId, extracted, file.getOriginalFilename());
            return toWorkspace(saved, extracted);
        }
        return toWorkspace(null, extracted);
    }

    @Transactional
    public ApWorkspaceResponse bindPurchaseOrder(UUID purchaseOrderId, Map<String, Object> extractedData) {
        Map<String, Object> extracted = extractedData != null ? new LinkedHashMap<>(extractedData) : new LinkedHashMap<>();
        extracted.put("matchedPurchaseOrderId", purchaseOrderId.toString());
        SupplierInvoiceIngestion saved = ocrIngestionService.submitDocument(purchaseOrderId, extracted, null);
        return toWorkspace(saved, extracted);
    }

    @Transactional
    public ApWorkspaceResponse approve(UUID ingestionId, List<Map<String, Object>> editedLines) {
        SupplierInvoiceIngestion ingestion = requireIngestion(ingestionId);
        Map<String, Object> data = new LinkedHashMap<>(ingestion.getExtractedData());
        if (editedLines != null && !editedLines.isEmpty()) {
            data.put("lines", editedLines);
        }
        ApWorkspaceResponse preview = toWorkspace(ingestion, data);
        if (!preview.allMatched()) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_MATCHED",
                    "Approve & Match requires every line within tolerance");
        }
        data.put("voucherApproved", true);
        data.put("voucherStatus", "APPROVED_FOR_PAYMENT");
        ingestion.setExtractedData(data);
        ingestion.setStatus("MATCHED");
        ingestion.setMatchConfidence(new BigDecimal("100.00"));
        ingestionRepository.save(ingestion);
        return toWorkspace(ingestion, data);
    }

    @Transactional
    public DisputeResponse dispute(UUID ingestionId) {
        SupplierInvoiceIngestion ingestion = requireIngestion(ingestionId);
        PurchaseOrder po = purchaseOrderRepository.findById(ingestion.getPurchaseOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
        Map<String, Object> data = new LinkedHashMap<>(ingestion.getExtractedData());
        String invoiceNumber = stringVal(data.get("invoiceNumber"));
        String letter = """
                weGrowStock vendor dispute — debit memo requested

                Purchase order: %s
                Invoice: %s
                The vendor invoice quantity or unit price does not match our PO and dock receipt.
                Please issue a debit memo / credit for the overbilled amount. Do not pay the original bill as-is.
                """.formatted(po.getNumber(), invoiceNumber != null ? invoiceNumber : "unnumbered");
        data.put("disputeLetter", letter);
        data.put("rtvPath", "/purchasing/rtv");
        ingestion.setExtractedData(data);
        ingestion.setStatus("DISPUTED");
        ingestionRepository.save(ingestion);
        return new DisputeResponse(ingestion.getId(), "DISPUTED", letter, "/purchasing/rtv");
    }

    @Transactional
    public RecountResponse requestRecount(UUID ingestionId) {
        SupplierInvoiceIngestion ingestion = requireIngestion(ingestionId);
        PurchaseOrder po = purchaseOrderRepository.findById(ingestion.getPurchaseOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
        UUID locationId = po.getDestinationLocationId();
        if (locationId == null) {
            locationId = locationRepository.findByTenantIdOrderByPathAsc(TenantContext.requireTenantId()).stream()
                    .map(Location::getId)
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_LOCATION",
                            "No warehouse location to count"));
        }
        CycleCountService.CycleCountDetail count = cycleCountService.startCount(locationId);
        Map<String, Object> data = new LinkedHashMap<>(ingestion.getExtractedData());
        data.put("recountCycleCountId", count.id().toString());
        ingestion.setExtractedData(data);
        ingestionRepository.save(ingestion);
        return new RecountResponse(count.id(), "/inventory/variances");
    }

    public ApWorkspaceResponse preview(UUID purchaseOrderId, Map<String, Object> extractedData) {
        Map<String, Object> extracted = extractedData != null ? extractedData : Map.of();
        SupplierInvoiceIngestion stub = new SupplierInvoiceIngestion();
        stub.setPurchaseOrderId(purchaseOrderId);
        stub.setStatus("PENDING");
        stub.setExtractedData(new LinkedHashMap<>(extracted));
        return toWorkspace(stub, extracted);
    }

    private SupplierInvoiceIngestion requireIngestion(UUID ingestionId) {
        return ingestionRepository.findById(ingestionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ingestion not found"));
    }

    @SuppressWarnings("unchecked")
    private ApWorkspaceResponse toWorkspace(SupplierInvoiceIngestion ingestion, Map<String, Object> extracted) {
        UUID poId = ingestion != null ? ingestion.getPurchaseOrderId() : null;
        PurchaseOrder po = poId != null ? purchaseOrderRepository.findById(poId).orElse(null) : null;
        List<PurchaseOrderLine> poLines = poId != null ? lineRepository.findByPurchaseOrderId(poId) : List.of();
        List<Map<String, Object>> invoiceLines = extracted.get("lines") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();

        List<MatchRow> rows = new ArrayList<>();
        for (PurchaseOrderLine poLine : poLines) {
            ProductVariant variant = variantRepository.findById(poLine.getVariantId()).orElse(null);
            String sku = variant != null ? variant.getSku() : "";
            String description = sku;
            if (variant != null) {
                description = productRepository.findById(variant.getProductId())
                        .map(Product::getName)
                        .filter(n -> n != null && !n.isBlank())
                        .orElse(sku);
            }
            Map<String, Object> invoice = invoiceLines.stream()
                    .filter(l -> sku.equalsIgnoreCase(String.valueOf(l.getOrDefault("sku", ""))))
                    .findFirst()
                    .orElse(null);
            BigDecimal invoicedQty = invoice != null ? toDecimal(invoice.get("qty")) : BigDecimal.ZERO;
            BigDecimal invoicedPrice = invoice != null ? toDecimal(invoice.get("unitCost")) : BigDecimal.ZERO;
            BigDecimal confidence = invoice != null ? toDecimal(invoice.get("confidence")) : BigDecimal.ZERO;
            if (confidence.signum() == 0 && invoice != null) {
                confidence = new BigDecimal("0.92");
            }
            BigDecimal received = poLine.getQtyReceived() != null ? poLine.getQtyReceived() : BigDecimal.ZERO;
            String matchStatus = lineStatus(poLine.getQtyOrdered(), received, invoicedQty,
                    poLine.getUnitCost(), invoicedPrice, invoice != null);
            rows.add(new MatchRow(
                    sku,
                    description,
                    poLine.getQtyOrdered(),
                    received,
                    invoicedQty,
                    poLine.getUnitCost(),
                    invoicedPrice,
                    matchStatus,
                    confidence.compareTo(LOW_CONFIDENCE) < 0,
                    confidence
            ));
        }
        boolean allMatched = !rows.isEmpty() && rows.stream().allMatch(r -> "MATCHED".equals(r.matchStatus()));
        boolean priceVar = rows.stream().anyMatch(r -> "PRICE_VARIANCE".equals(r.matchStatus()));
        boolean qtyVar = rows.stream().anyMatch(r -> "QTY_VARIANCE".equals(r.matchStatus()));
        boolean receivedShort = rows.stream().anyMatch(r ->
                r.receivedQty().compareTo(r.invoicedQty()) < 0
                        && r.invoicedQty().subtract(r.receivedQty()).abs().compareTo(QTY_TOLERANCE) > 0);

        Header header = new Header(
                stringVal(extracted.get("invoiceNumber")),
                stringVal(extracted.get("invoiceDate")),
                stringVal(extracted.get("supplierName")),
                decimalOrNull(extracted.get("subtotal")),
                decimalOrNull(extracted.get("tax")),
                stringVal(extracted.get("detectedPoNumber"))
        );
        return new ApWorkspaceResponse(
                ingestion != null ? ingestion.getId() : null,
                poId,
                po != null ? po.getNumber() : null,
                ingestion != null ? ingestion.getStatus() : "DRAFT",
                header,
                rows,
                allMatched,
                priceVar,
                qtyVar,
                receivedShort
        );
    }

    private static String lineStatus(BigDecimal poQty,
                                     BigDecimal received,
                                     BigDecimal invoicedQty,
                                     BigDecimal poPrice,
                                     BigDecimal invoicedPrice,
                                     boolean hasInvoice) {
        if (!hasInvoice) {
            return "QTY_VARIANCE";
        }
        boolean qtyOk = invoicedQty.subtract(poQty).abs().compareTo(QTY_TOLERANCE) <= 0
                && received.subtract(poQty).abs().compareTo(QTY_TOLERANCE) <= 0;
        boolean priceOk = poPrice == null || poPrice.signum() == 0
                || withinPrice(invoicedPrice, poPrice);
        if (qtyOk && priceOk) {
            return "MATCHED";
        }
        if (!priceOk) {
            return "PRICE_VARIANCE";
        }
        return "QTY_VARIANCE";
    }

    private static boolean withinPrice(BigDecimal invoiceCost, BigDecimal poCost) {
        if (invoiceCost.compareTo(poCost) == 0) {
            return true;
        }
        if (poCost.signum() == 0) {
            return invoiceCost.signum() == 0;
        }
        BigDecimal pct = invoiceCost.subtract(poCost).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(poCost, 4, RoundingMode.HALF_UP);
        return pct.compareTo(PRICE_TOLERANCE_PCT) <= 0;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal decimalOrNull(Object value) {
        if (value == null) {
            return null;
        }
        BigDecimal d = toDecimal(value);
        return d.signum() == 0 && !(value instanceof Number) ? null : d;
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record Header(
            String invoiceNumber,
            String invoiceDate,
            String supplierName,
            BigDecimal subtotal,
            BigDecimal tax,
            String detectedPoNumber
    ) {
    }

    public record MatchRow(
            String sku,
            String description,
            BigDecimal poQty,
            BigDecimal receivedQty,
            BigDecimal invoicedQty,
            BigDecimal poUnitPrice,
            BigDecimal invoicedPrice,
            String matchStatus,
            boolean lowConfidence,
            BigDecimal confidence
    ) {
    }

    public record ApWorkspaceResponse(
            UUID ingestionId,
            UUID purchaseOrderId,
            String purchaseOrderNumber,
            String status,
            Header header,
            List<MatchRow> lines,
            boolean allMatched,
            boolean hasPriceVariance,
            boolean hasQtyVariance,
            boolean receivedLessThanInvoiced
    ) {
    }

    public record DisputeResponse(UUID ingestionId, String status, String disputeLetter, String rtvPath) {
    }

    public record RecountResponse(UUID cycleCountId, String variancePath) {
    }
}

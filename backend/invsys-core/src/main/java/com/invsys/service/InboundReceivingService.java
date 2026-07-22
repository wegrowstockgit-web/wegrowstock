package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.domain.TenantSettings;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.rtls.RtlsTelemetryService;
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
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.modules.purchasing.service.PurchaseOrderService;

/**
 * Mobile inbound receiving: PO/ASN lookup → item scan → directed putaway → PO_RECEIPT ledger.
 */
@Service
public class InboundReceivingService {

    public static final String REASON_PO_RECEIPT = "PO_RECEIPT";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationRepository locationRepository;
    private final InventoryService inventoryService;
    private final PutawayStrategyService putawayStrategyService;
    private final UomConversionService uomConversionService;
    private final PurchaseOrderService purchaseOrderService;
    private final RtlsTelemetryService rtlsTelemetryService;
    private final TenantSettingsRepository tenantSettingsRepository;

    public InboundReceivingService(PurchaseOrderRepository purchaseOrderRepository,
                                   PurchaseOrderLineRepository lineRepository,
                                   ProductVariantRepository variantRepository,
                                   LocationRepository locationRepository,
                                   InventoryService inventoryService,
                                   PutawayStrategyService putawayStrategyService,
                                   UomConversionService uomConversionService,
                                   PurchaseOrderService purchaseOrderService,
                                   RtlsTelemetryService rtlsTelemetryService,
                                   TenantSettingsRepository tenantSettingsRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.inventoryService = inventoryService;
        this.putawayStrategyService = putawayStrategyService;
        this.uomConversionService = uomConversionService;
        this.purchaseOrderService = purchaseOrderService;
        this.rtlsTelemetryService = rtlsTelemetryService;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    @Transactional(readOnly = true)
    public InboundPoView lookupPo(String barcode) {
        UUID tenantId = TenantContext.requireTenantId();
        String trimmed = requireBarcode(barcode);
        PurchaseOrder po = purchaseOrderRepository.findByTenantIdAndNumberIgnoreCase(tenantId, trimmed)
                .or(() -> {
                    try {
                        return purchaseOrderRepository.findByTenantIdAndId(tenantId, UUID.fromString(trimmed));
                    } catch (IllegalArgumentException ex) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PO_NOT_FOUND",
                        "No purchase order / ASN matches that barcode"));

        if (!List.of("SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED").contains(po.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Receiving requires SUBMITTED, IN_TRANSIT, or PARTIALLY_RECEIVED status");
        }
        return toPoView(po);
    }

    @Transactional(readOnly = true)
    public InboundPoView getPo(UUID poId) {
        PurchaseOrder po = requirePo(poId);
        return toPoView(po);
    }

    @Transactional(readOnly = true)
    public InboundLineMatch resolveItem(UUID poId, String itemBarcode) {
        PurchaseOrder po = requirePo(poId);
        UUID tenantId = TenantContext.requireTenantId();
        String barcode = requireBarcode(itemBarcode);
        ProductVariant variant = variantRepository.findByTenantIdAndBarcode(tenantId, barcode)
                .or(() -> variantRepository.findByTenantIdAndSku(tenantId, barcode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND",
                        "No SKU / UPC / EAN matches that scan on this PO"));

        PurchaseOrderLine line = lineRepository.findByPurchaseOrderId(po.getId()).stream()
                .filter(l -> l.getVariantId().equals(variant.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ITEM_NOT_ON_PO",
                        "Scanned item is not on this purchase order"));

        BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyReceived());
        if (remaining.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LINE_FULLY_RECEIVED",
                    "This PO line is already fully received");
        }
        return new InboundLineMatch(
                line.getId(),
                variant.getId(),
                variant.getSku(),
                variant.getBarcode(),
                line.getQtyOrdered(),
                line.getQtyReceived(),
                remaining);
    }

    @Transactional(readOnly = true)
    public PutawayStrategyService.PutawayDirective suggestPutaway(UUID variantId) {
        return putawayStrategyService.suggest(variantId);
    }

    /**
     * Confirms directed putaway: validates bin scan, appends {@code PO_RECEIPT} ledger, triggers RTLS.
     */
    @Transactional
    public ConfirmPutawayResult confirmPutaway(ConfirmPutawayRequest request) {
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "quantity must be positive");
        }
        PurchaseOrderLine line = lineRepository.findById(request.lineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "PO line not found"));
        PurchaseOrder po = requirePo(line.getPurchaseOrderId());
        if (!List.of("SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED").contains(po.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Receiving requires SUBMITTED, IN_TRANSIT, or PARTIALLY_RECEIVED status");
        }

        PutawayStrategyService.PutawayDirective suggested = putawayStrategyService.suggest(line.getVariantId());
        Location location = resolveConfirmLocation(request, suggested);

        BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyReceived());
        BigDecimal tolerancePercent = overReceiptTolerancePercent();
        BigDecimal maxAllowed = remaining.multiply(
                BigDecimal.ONE.add(tolerancePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
        if (request.quantity().compareTo(maxAllowed) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "OVER_RECEIPT_TOLERANCE",
                    "Quantity exceeds over-receipt tolerance");
        }

        BigDecimal standardQty = uomConversionService.toStandardQuantity(
                line.getVariantId(), request.quantity(), "PURCHASING");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("poNumber", po.getNumber());
        metadata.put("putawayStrategy", suggested.strategy());
        if (request.tagId() != null && !request.tagId().isBlank()) {
            metadata.put("rtlsTagId", request.tagId().trim());
        }

        InventoryLedger ledger = inventoryService.receive(
                line.getVariantId(),
                location.getId(),
                request.lotId(),
                null,
                standardQty,
                REASON_PO_RECEIPT,
                "PURCHASE_ORDER",
                po.getId(),
                line.getUnitCost(),
                null,
                metadata);

        line.setQtyReceived(line.getQtyReceived().add(request.quantity()));
        lineRepository.save(line);
        purchaseOrderService.refreshStatusPublic(po);

        boolean rtlsTriggered = false;
        if (request.tagId() != null && !request.tagId().isBlank()) {
            rtlsTelemetryService.bindPalletTag(request.tagId().trim(), po.getId(), "PO " + po.getNumber());
            rtlsTriggered = rtlsTelemetryService.announceAssetAtLocation(po.getId(), location.getId()).isPresent();
        } else {
            rtlsTriggered = rtlsTelemetryService.announceAssetAtLocation(po.getId(), location.getId()).isPresent();
        }

        return new ConfirmPutawayResult(
                ledger.getId(),
                line.getId(),
                po.getId(),
                po.getNumber(),
                location.getId(),
                location.getCode(),
                location.getPath(),
                standardQty,
                REASON_PO_RECEIPT,
                suggested.strategy(),
                rtlsTriggered);
    }

    private Location resolveConfirmLocation(ConfirmPutawayRequest request,
                                            PutawayStrategyService.PutawayDirective suggested) {
        UUID tenantId = TenantContext.requireTenantId();
        Location scanned = null;
        if (request.scannedLocationBarcode() != null && !request.scannedLocationBarcode().isBlank()) {
            String code = request.scannedLocationBarcode().trim();
            scanned = locationRepository.findByTenantIdAndCode(tenantId, code)
                    .or(() -> locationRepository.findByTenantIdAndPath(tenantId, code))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND",
                            "No bin matches that location scan"));
        } else if (request.locationId() != null) {
            scanned = locationRepository.findById(request.locationId())
                    .filter(l -> tenantId.equals(l.getTenantId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND",
                            "Putaway location not found"));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "locationId or scannedLocationBarcode is required");
        }

        boolean matchesSuggested = suggested.locationId().equals(scanned.getId())
                || (suggested.code() != null && suggested.code().equalsIgnoreCase(scanned.getCode()));
        if (!matchesSuggested) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PUTAWAY_LOCATION_MISMATCH",
                    "Scan the directed bin " + suggested.code() + " (" + suggested.path() + ")");
        }
        return scanned;
    }

    private InboundPoView toPoView(PurchaseOrder po) {
        List<InboundLineView> lines = new ArrayList<>();
        for (PurchaseOrderLine line : lineRepository.findByPurchaseOrderId(po.getId())) {
            ProductVariant variant = variantRepository.findById(line.getVariantId()).orElse(null);
            lines.add(new InboundLineView(
                    line.getId(),
                    line.getVariantId(),
                    variant != null ? variant.getSku() : null,
                    variant != null ? variant.getBarcode() : null,
                    line.getQtyOrdered(),
                    line.getQtyReceived(),
                    line.getQtyOrdered().subtract(line.getQtyReceived())));
        }
        return new InboundPoView(po.getId(), po.getNumber(), po.getStatus(), lines);
    }

    private PurchaseOrder requirePo(UUID poId) {
        UUID tenantId = TenantContext.requireTenantId();
        return purchaseOrderRepository.findByTenantIdAndId(tenantId, poId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PO_NOT_FOUND", "Purchase order not found"));
    }

    private BigDecimal overReceiptTolerancePercent() {
        return tenantSettingsRepository.findByTenantId(TenantContext.requireTenantId())
                .map(TenantSettings::getSettings)
                .map(settings -> settings.get("over_receipt_tolerance_percent"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(n -> BigDecimal.valueOf(n.doubleValue()))
                .orElse(BigDecimal.ZERO);
    }

    private static String requireBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "barcode is required");
        }
        return barcode.trim();
    }

    public record InboundPoView(UUID id, String number, String status, List<InboundLineView> lines) {
    }

    public record InboundLineView(
            UUID lineId,
            UUID variantId,
            String sku,
            String barcode,
            BigDecimal qtyOrdered,
            BigDecimal qtyReceived,
            BigDecimal qtyRemaining
    ) {
    }

    public record InboundLineMatch(
            UUID lineId,
            UUID variantId,
            String sku,
            String barcode,
            BigDecimal qtyOrdered,
            BigDecimal qtyReceived,
            BigDecimal qtyRemaining
    ) {
    }

    public record ConfirmPutawayRequest(
            UUID lineId,
            BigDecimal quantity,
            UUID locationId,
            String scannedLocationBarcode,
            UUID lotId,
            String tagId
    ) {
    }

    public record ConfirmPutawayResult(
            UUID ledgerId,
            UUID lineId,
            UUID purchaseOrderId,
            String poNumber,
            UUID locationId,
            String locationCode,
            String locationPath,
            BigDecimal quantityChange,
            String action,
            String putawayStrategy,
            boolean rtlsTriggered
    ) {
    }
}

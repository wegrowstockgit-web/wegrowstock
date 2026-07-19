package com.invsys.service;

import com.invsys.api.FulfillmentController.ScanRequest;
import com.invsys.api.FulfillmentController.ScanResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.metrics.WmsMetrics;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Floor fulfillment operations (scan receive/pick) with WMS domain metrics.
 */
@Service
public class FulfillmentService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ScanService scanService;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AllocationService allocationService;
    private final CrossDockService crossDockService;
    private final MeterRegistry meterRegistry;
    private final WmsMetrics wmsMetrics;

    public FulfillmentService(ProductVariantRepository variantRepository,
                              ProductRepository productRepository,
                              InventoryService inventoryService,
                              ScanService scanService,
                              TenantSettingsRepository tenantSettingsRepository,
                              AllocationService allocationService,
                              CrossDockService crossDockService,
                              MeterRegistry meterRegistry,
                              WmsMetrics wmsMetrics) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.scanService = scanService;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.allocationService = allocationService;
        this.crossDockService = crossDockService;
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.wmsMetrics = wmsMetrics;
    }

    @Transactional
    public ScanResponse executeScan(ScanRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        final String lookupKey = (request.barcode() == null || request.barcode().isBlank())
                && request.gtin() != null && !request.gtin().isBlank()
                ? request.gtin()
                : request.barcode();
        ProductVariant variant = variantRepository.findByTenantIdAndBarcode(tenantId, lookupKey)
                .or(() -> variantRepository.findByTenantIdAndSku(tenantId, lookupKey))
                .or(() -> {
                    String gtin = request.gtin();
                    if (gtin == null || gtin.isBlank() || gtin.equals(lookupKey)) {
                        return java.util.Optional.empty();
                    }
                    return variantRepository.findByTenantIdAndBarcode(tenantId, gtin);
                })
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Barcode not found"));

        String productName = productRepository.findById(variant.getProductId())
                .map(Product::getName)
                .orElse(variant.getSku());
        String putawayTarget = scanService.resolvePutawayPath(variant);
        String primaryMediaUrl = scanService.primaryMediaUrl(variant.getId());
        BigDecimal qty = request.quantity() != null && request.quantity().signum() > 0
                ? request.quantity()
                : BigDecimal.ONE;

        if ("receive".equalsIgnoreCase(request.mode())) {
            return receiveScan(request, variant, productName, putawayTarget, primaryMediaUrl, qty);
        }
        return pickScan(request, variant, productName, putawayTarget, primaryMediaUrl, qty);
    }

    private ScanResponse receiveScan(ScanRequest request,
                                     ProductVariant variant,
                                     String productName,
                                     String putawayTarget,
                                     String primaryMediaUrl,
                                     BigDecimal qty) {
        CrossDockService.CrossDockTask crossDock = crossDockService.checkVariant(variant.getId());
        if (crossDock.match()) {
            String stagingPath = crossDock.stagingPath() != null ? crossDock.stagingPath() : putawayTarget;
            String message = crossDock.instruction() != null
                    ? crossDock.instruction()
                    : "Route to Shipping Staging Lane";
            return new ScanResponse(
                    variant.getId(),
                    variant.getSku(),
                    productName,
                    false,
                    null,
                    message,
                    stagingPath,
                    primaryMediaUrl,
                    variant.isLotTracked(),
                    false,
                    true,
                    stagingPath,
                    crossDock.stagingHintLocationId(),
                    crossDock.salesOrderNumber(),
                    crossDock.instruction());
        }
        if (!allowBlindReceiving()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_RECEIVING_DISABLED",
                    "Blind receiving is disabled for this tenant");
        }
        if (variant.isTrackSerials() && (request.serialNumber() == null || request.serialNumber().isBlank())) {
            return new ScanResponse(variant.getId(), variant.getSku(), productName, true, "SERIAL_REQUIRED",
                    "Scan serial numbers one at a time", putawayTarget, primaryMediaUrl,
                    variant.isLotTracked(), false, false, null, null, null, null);
        }
        InventoryService.ResolvedLot preview = inventoryService.resolveLot(
                variant, null, request.lotNumber(), request.metadata());
        inventoryService.receive(variant.getId(), request.warehouseId(), null, request.lotNumber(),
                qty, "SCAN_RECEIVE", null, null, request.serialNumber(), request.metadata());
        String message = request.serialNumber() != null
                ? "Received serial " + request.serialNumber()
                : "Received " + qty.stripTrailingZeros().toPlainString() + " unit(s)";
        return new ScanResponse(variant.getId(), variant.getSku(), productName, false, null, message,
                putawayTarget, primaryMediaUrl, variant.isLotTracked(), preview.lotLoggedNotTracked(),
                false, null, null, null, null);
    }

    private ScanResponse pickScan(ScanRequest request,
                                  ProductVariant variant,
                                  String productName,
                                  String putawayTarget,
                                  String primaryMediaUrl,
                                  BigDecimal qty) {
        if (variant.isTrackSerials() && (request.serialNumber() == null || request.serialNumber().isBlank())) {
            return new ScanResponse(variant.getId(), variant.getSku(), productName, true, "SERIAL_REQUIRED",
                    "Scan serial numbers one at a time", putawayTarget, primaryMediaUrl,
                    variant.isLotTracked(), false, false, null, null, null, null);
        }
        Timer.Sample sample = wmsMetrics.startAllocation();
        try {
            Allocation allocation = allocationService.assertPickableForCurrentUser(
                    variant.getId(), request.allocationId());
            UUID pickLocationId = allocation != null ? allocation.getLocationId() : request.warehouseId();
            UUID pickLotId = allocation != null ? allocation.getLotId() : null;
            InventoryService.ResolvedLot preview = inventoryService.resolveLot(
                    variant, pickLotId, request.lotNumber(), request.metadata());
            try {
                if (allocation != null) {
                    inventoryService.adjustReserved(
                            variant.getId(), pickLocationId, pickLotId, request.lotNumber(),
                            qty.negate(), "SCAN_PICK", request.serialNumber(), request.metadata());
                    allocationService.consumeForPick(allocation, qty);
                } else {
                    inventoryService.adjust(variant.getId(), pickLocationId, pickLotId, request.lotNumber(),
                            qty.negate(), "SCAN_PICK", request.serialNumber(), request.metadata());
                }
            } catch (com.invsys.common.exception.InsufficientStockException ex) {
                throw new com.invsys.common.exception.InsufficientStockException("Insufficient stock");
            }
            String message = request.serialNumber() != null
                    ? "Picked serial " + request.serialNumber()
                    : "Picked " + qty.stripTrailingZeros().toPlainString() + " unit(s)";
            return new ScanResponse(variant.getId(), variant.getSku(), productName, false, null, message,
                    putawayTarget, primaryMediaUrl, variant.isLotTracked(), preview.lotLoggedNotTracked(),
                    false, null, null, null, null);
        } finally {
            wmsMetrics.stopAllocation(sample);
        }
    }

    private boolean allowBlindReceiving() {
        return tenantSettingsRepository.findByTenantId(TenantContext.requireTenantId())
                .map(TenantSettings::getSettings)
                .map(settings -> settings.get("allow_blind_receiving"))
                .map(value -> {
                    if (value instanceof Boolean b) {
                        return b;
                    }
                    return Boolean.parseBoolean(String.valueOf(value));
                })
                .orElse(false);
    }
}

package com.invsys.api;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.service.AllocationService;
import com.invsys.service.IdempotencyService;
import com.invsys.service.InventoryService;
import com.invsys.service.ScanService;
import com.invsys.tenancy.TenantContext;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfillment")
public class FulfillmentController {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ScanService scanService;
    private final IdempotencyService idempotencyService;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AllocationService allocationService;

    public FulfillmentController(ProductVariantRepository variantRepository,
                                 ProductRepository productRepository,
                                 InventoryService inventoryService,
                                 ScanService scanService,
                                 IdempotencyService idempotencyService,
                                 TenantSettingsRepository tenantSettingsRepository,
                                 AllocationService allocationService) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.scanService = scanService;
        this.idempotencyService = idempotencyService;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.allocationService = allocationService;
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    @Timed(value = "invsys.fulfillment.scan", description = "Warehouse scan receive/pick latency")
    public ResponseEntity<ScanResponse> scan(
            @Valid @RequestBody ScanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_REQUIRED", "Idempotency-Key header is required");
        }
        var cached = idempotencyService.find(idempotencyKey.trim());
        if (cached.isPresent()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = cached.get().body();
            ScanResponse replayed = new ScanResponse(
                    body.get("variantId") != null ? UUID.fromString(String.valueOf(body.get("variantId"))) : null,
                    (String) body.get("sku"),
                    (String) body.get("name"),
                    Boolean.TRUE.equals(body.get("requiresSerial")),
                    (String) body.get("serialPrompt"),
                    (String) body.get("message"),
                    (String) body.get("putawayTarget"),
                    (String) body.get("primaryMediaUrl"));
            return ResponseEntity.status(cached.get().status()).body(replayed);
        }

        ScanResponse response = executeScan(request);
        idempotencyService.store(
                idempotencyKey.trim(),
                request.barcode() + "|" + request.mode() + "|" + request.warehouseId(),
                HttpStatus.OK.value(),
                toMap(response));
        return ResponseEntity.ok(response);
    }

    private ScanResponse executeScan(ScanRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        // Client-side GS1 parser sends GTIN as barcode (+ optional gtin); no server re-parse required.
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

        String message;
        if ("receive".equalsIgnoreCase(request.mode())) {
            if (!allowBlindReceiving()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_RECEIVING_DISABLED",
                        "Blind receiving is disabled for this tenant");
            }
            if (variant.isTrackSerials() && (request.serialNumber() == null || request.serialNumber().isBlank())) {
                return new ScanResponse(variant.getId(), variant.getSku(), productName, true, "SERIAL_REQUIRED",
                        "Scan serial numbers one at a time", putawayTarget, primaryMediaUrl);
            }
            inventoryService.receive(variant.getId(), request.warehouseId(), null,
                    qty, "SCAN_RECEIVE", null, null, request.serialNumber());
            message = request.serialNumber() != null
                    ? "Received serial " + request.serialNumber()
                    : "Received " + qty.stripTrailingZeros().toPlainString() + " unit(s)";
        } else {
            if (variant.isTrackSerials() && (request.serialNumber() == null || request.serialNumber().isBlank())) {
                return new ScanResponse(variant.getId(), variant.getSku(), productName, true, "SERIAL_REQUIRED",
                        "Scan serial numbers one at a time", putawayTarget, primaryMediaUrl);
            }
            Allocation allocation = allocationService.assertPickableForCurrentUser(
                    variant.getId(), request.allocationId());
            try {
                inventoryService.adjust(variant.getId(), request.warehouseId(), null,
                        qty.negate(), "SCAN_PICK", request.serialNumber());
            } catch (ApiException ex) {
                if ("INSUFFICIENT_STOCK".equals(ex.getCode())) {
                    throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Insufficient stock")
                            .withProperty("reason", "Insufficient stock")
                            .withProperty("variantId", variant.getId().toString());
                }
                throw ex;
            }
            allocationService.consumeForPick(allocation, qty);
            message = request.serialNumber() != null
                    ? "Picked serial " + request.serialNumber()
                    : "Picked " + qty.stripTrailingZeros().toPlainString() + " unit(s)";
        }
        return new ScanResponse(variant.getId(), variant.getSku(), productName, false, null, message, putawayTarget, primaryMediaUrl);
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

    private static Map<String, Object> toMap(ScanResponse response) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variantId", response.variantId() != null ? response.variantId().toString() : null);
        map.put("sku", response.sku());
        map.put("name", response.name());
        map.put("requiresSerial", response.requiresSerial());
        map.put("serialPrompt", response.serialPrompt());
        map.put("message", response.message());
        map.put("putawayTarget", response.putawayTarget());
        map.put("primaryMediaUrl", response.primaryMediaUrl());
        return map;
    }

    /**
     * Floor scan payload. When the PWA decodes GS1-128 offline, it sends structured
     * {@code gtin}/{@code lotNumber}/{@code expiryDate}/{@code quantity} so the API
     * does not need to re-parse the composite AI string on replay.
     */
    public record ScanRequest(
            @NotBlank String barcode,
            @NotNull UUID warehouseId,
            @NotBlank String mode,
            String serialNumber,
            UUID allocationId,
            String gtin,
            String lotNumber,
            String expiryDate,
            BigDecimal quantity,
            Boolean isGs1,
            String rawBarcode
    ) {
    }

    public record ScanResponse(
            UUID variantId,
            String sku,
            String name,
            boolean requiresSerial,
            String serialPrompt,
            String message,
            String putawayTarget,
            String primaryMediaUrl
    ) {
    }
}

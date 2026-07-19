package com.invsys.api;

import com.invsys.common.ApiException;
import com.invsys.service.CrossDockService;
import com.invsys.service.FulfillmentExceptionService;
import com.invsys.service.FulfillmentService;
import com.invsys.service.IdempotencyService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfillment")
public class FulfillmentController {

    private final IdempotencyService idempotencyService;
    private final FulfillmentExceptionService fulfillmentExceptionService;
    private final CrossDockService crossDockService;
    private final FulfillmentService fulfillmentService;

    public FulfillmentController(IdempotencyService idempotencyService,
                                 FulfillmentExceptionService fulfillmentExceptionService,
                                 CrossDockService crossDockService,
                                 FulfillmentService fulfillmentService) {
        this.idempotencyService = idempotencyService;
        this.fulfillmentExceptionService = fulfillmentExceptionService;
        this.crossDockService = crossDockService;
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * On receive: if sales demand is already allocated, bypass storage and stage for shipping.
     * POST-only — mutates allocation status (GET would be unsafe / cacheable).
     */
    @PostMapping("/cross-dock/check")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public CrossDockCheckResponse crossDockCheck(@RequestParam UUID variantId) {
        CrossDockService.CrossDockTask task = crossDockService.checkVariant(variantId);
        return new CrossDockCheckResponse(
                task.match(),
                task.variantId(),
                task.sku(),
                task.allocationId(),
                task.salesOrderId(),
                task.salesOrderNumber(),
                task.salesOrderLineId(),
                task.stagingHintLocationId(),
                task.stagingPath(),
                task.quantity(),
                task.allocationStatus(),
                task.instruction());
    }

    public record CrossDockCheckResponse(
            boolean match,
            UUID variantId,
            String sku,
            UUID allocationId,
            UUID salesOrderId,
            String salesOrderNumber,
            UUID salesOrderLineId,
            UUID stagingHintLocationId,
            String stagingPath,
            BigDecimal quantity,
            String allocationStatus,
            String instruction
    ) {
    }

    @PostMapping("/exceptions/report")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public ExceptionReportResponse reportException(@Valid @RequestBody ExceptionReportRequest request) {
        FulfillmentExceptionService.ReportResult result = fulfillmentExceptionService.reportDamagedBarcode(
                request.allocationId(), request.metadata());
        return new ExceptionReportResponse(
                result.exceptionId(),
                result.allocationId(),
                result.resolutionStatus(),
                result.alreadyReported(),
                FulfillmentExceptionService.STATUS_EXCEPTION);
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
                    (String) body.get("primaryMediaUrl"),
                    Boolean.TRUE.equals(body.get("isLotTracked")),
                    Boolean.TRUE.equals(body.get("lotLoggedNotTracked")),
                    Boolean.TRUE.equals(body.get("crossDock")),
                    (String) body.get("stagingPath"),
                    body.get("stagingLocationId") != null
                            ? UUID.fromString(String.valueOf(body.get("stagingLocationId"))) : null,
                    (String) body.get("crossDockSalesOrderNumber"),
                    (String) body.get("crossDockInstruction"));
            return ResponseEntity.status(cached.get().status()).body(replayed);
        }

        ScanResponse response = fulfillmentService.executeScan(request);
        idempotencyService.store(
                idempotencyKey.trim(),
                request.barcode() + "|" + request.mode() + "|" + request.warehouseId(),
                HttpStatus.OK.value(),
                toMap(response));
        return ResponseEntity.ok(response);
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
        map.put("isLotTracked", response.isLotTracked());
        map.put("lotLoggedNotTracked", response.lotLoggedNotTracked());
        map.put("crossDock", response.crossDock());
        map.put("stagingPath", response.stagingPath());
        map.put("stagingLocationId",
                response.stagingLocationId() != null ? response.stagingLocationId().toString() : null);
        map.put("crossDockSalesOrderNumber", response.crossDockSalesOrderNumber());
        map.put("crossDockInstruction", response.crossDockInstruction());
        return map;
    }

    /**
     * Floor scan payload. When the PWA decodes GS1-128 offline, it sends structured
     * {@code gtin}/{@code lotNumber}/{@code expiryDate}/{@code quantity} so the API
     * does not need to re-parse the composite AI string on replay.
     * {@code metadata} may include {@code vendor_lot_captured} when the client already
     * decided the variant is not lot-tracked.
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
            String rawBarcode,
            Map<String, Object> metadata
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
            String primaryMediaUrl,
            boolean isLotTracked,
            boolean lotLoggedNotTracked,
            boolean crossDock,
            String stagingPath,
            UUID stagingLocationId,
            String crossDockSalesOrderNumber,
            String crossDockInstruction
    ) {
    }

    public record ExceptionReportRequest(
            @NotNull UUID allocationId,
            Map<String, Object> metadata
    ) {
    }

    public record ExceptionReportResponse(
            UUID exceptionId,
            UUID allocationId,
            String resolutionStatus,
            boolean alreadyReported,
            String allocationStatus
    ) {
    }
}

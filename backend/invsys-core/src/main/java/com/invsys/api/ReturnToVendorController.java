package com.invsys.api;

import com.invsys.domain.RtvOrder;
import com.invsys.domain.RtvOrderLine;
import com.invsys.service.ReturnToVendorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rtv")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ReturnToVendorController {

    private final ReturnToVendorService returnToVendorService;

    public ReturnToVendorController(ReturnToVendorService returnToVendorService) {
        this.returnToVendorService = returnToVendorService;
    }

    @GetMapping
    public List<RtvOrderResponse> list() {
        return returnToVendorService.listRtv().stream().map(this::toOrderResponse).toList();
    }

    @GetMapping("/{id}")
    public RtvDetailResponse get(@PathVariable UUID id) {
        ReturnToVendorService.RtvDetail detail = returnToVendorService.get(id);
        return new RtvDetailResponse(
                toOrderResponse(detail.order()),
                detail.lines().stream().map(this::toLineResponse).toList());
    }

    @PostMapping("/from-exception")
    public RtvDetailResponse createFromException(@Valid @RequestBody CreateFromExceptionBody body) {
        ReturnToVendorService.RtvDetail detail = returnToVendorService.createRtvFromException(
                body.exceptionId(),
                body.reasonCode(),
                body.qty(),
                body.supplierId(),
                body.purchaseOrderId());
        return new RtvDetailResponse(
                toOrderResponse(detail.order()),
                detail.lines().stream().map(this::toLineResponse).toList());
    }

    @PostMapping("/{id}/approve")
    public RtvOrderResponse approve(@PathVariable UUID id) {
        return toOrderResponse(returnToVendorService.approve(id));
    }

    @PostMapping("/{id}/ship")
    public RtvDetailResponse ship(@PathVariable UUID id, @Valid @RequestBody ShipBody body) {
        ReturnToVendorService.RtvDetail detail = returnToVendorService.shipRtv(
                id, body.carrier(), body.trackingNumber());
        return new RtvDetailResponse(
                toOrderResponse(detail.order()),
                detail.lines().stream().map(this::toLineResponse).toList());
    }

    private RtvOrderResponse toOrderResponse(RtvOrder order) {
        return new RtvOrderResponse(
                order.getId(),
                order.getSupplierId(),
                order.getPurchaseOrderId(),
                order.getNumber(),
                order.getStatus(),
                order.getDebitMemoNumber(),
                order.getTotalChargebackAmount(),
                order.getCarrier(),
                order.getTrackingNumber(),
                order.getExceptionId(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private RtvLineResponse toLineResponse(RtvOrderLine line) {
        return new RtvLineResponse(
                line.getId(),
                line.getRtvOrderId(),
                line.getVariantId(),
                line.getLotId(),
                line.getLocationId(),
                line.getQtyReturned(),
                line.getUnitCost(),
                line.getReasonCode());
    }

    public record CreateFromExceptionBody(
            @NotNull UUID exceptionId,
            @NotBlank String reasonCode,
            @NotNull BigDecimal qty,
            UUID supplierId,
            UUID purchaseOrderId
    ) {
    }

    public record ShipBody(
            String carrier,
            String trackingNumber
    ) {
    }

    public record RtvOrderResponse(
            UUID id,
            UUID supplierId,
            UUID purchaseOrderId,
            String number,
            String status,
            String debitMemoNumber,
            BigDecimal totalChargebackAmount,
            String carrier,
            String trackingNumber,
            UUID exceptionId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record RtvLineResponse(
            UUID id,
            UUID rtvOrderId,
            UUID variantId,
            UUID lotId,
            UUID locationId,
            BigDecimal qtyReturned,
            BigDecimal unitCost,
            String reasonCode
    ) {
    }

    public record RtvDetailResponse(RtvOrderResponse order, List<RtvLineResponse> lines) {
    }
}

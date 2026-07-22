package com.invsys.api;

import com.invsys.modules.fulfillment.domain.Shipment;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.service.CartonizationEngine;
import com.invsys.modules.fulfillment.service.ShipmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentRepository shipmentRepository, ShipmentService shipmentService) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentService = shipmentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<Shipment> list(@RequestParam UUID salesOrderId) {
        return shipmentRepository.findBySalesOrderId(salesOrderId);
    }

    @GetMapping("/cartonize-preview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public CartonPreviewResponse cartonizePreview(@RequestParam UUID salesOrderId) {
        CartonizationEngine.CartonizationResult result = shipmentService.previewCartonization(salesOrderId);
        return CartonPreviewResponse.from(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public Shipment create(@Valid @RequestBody CreateShipmentRequest request) {
        List<ShipmentService.ShipLineRequest> lines = request.lines() == null
                ? List.of()
                : request.lines().stream()
                        .map(l -> new ShipmentService.ShipLineRequest(l.salesOrderLineId(), l.quantity()))
                        .toList();
        return shipmentService.createShipment(request.salesOrderId(), request.carrier(),
                request.trackingNumber(), lines, request.lpnBarcode());
    }

    @PostMapping("/pack-label")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public Shipment createPackLabel(@Valid @RequestBody PackLabelRequest request) {
        return shipmentService.createPackLabel(
                request.salesOrderId(), request.totalWeightLb(), request.carrier());
    }

    public record PackLabelRequest(
            @NotNull UUID salesOrderId,
            BigDecimal totalWeightLb,
            String carrier
    ) {
    }

    public record CreateShipmentRequest(
            @NotNull UUID salesOrderId,
            String carrier,
            String trackingNumber,
            List<ShipLineRequest> lines,
            /** When set, ships all inventory_levels on the LPN and marks it DISPATCHED. */
            String lpnBarcode
    ) {
    }

    public record ShipLineRequest(@NotNull UUID salesOrderLineId, @NotNull BigDecimal quantity) {
    }

    public record PackPlacementResponse(
            UUID variantId,
            BigDecimal xIn,
            BigDecimal yIn,
            BigDecimal zIn,
            BigDecimal lengthIn,
            BigDecimal widthIn,
            BigDecimal heightIn
    ) {
    }

    public record CartonPreviewResponse(
            UUID cartonId,
            String cartonName,
            BigDecimal lengthIn,
            BigDecimal widthIn,
            BigDecimal heightIn,
            BigDecimal actualWeightLb,
            BigDecimal volumetricWeightLb,
            BigDecimal billableWeightLb,
            BigDecimal totalVolumeCuIn,
            List<PackPlacementResponse> packing
    ) {
        static CartonPreviewResponse from(CartonizationEngine.CartonizationResult result) {
            List<PackPlacementResponse> packing = result.packing() == null
                    ? List.of()
                    : result.packing().stream()
                            .map(p -> new PackPlacementResponse(
                                    p.variantId(), p.xIn(), p.yIn(), p.zIn(),
                                    p.lengthIn(), p.widthIn(), p.heightIn()))
                            .toList();
            return new CartonPreviewResponse(
                    result.carton().getId(),
                    result.carton().getName(),
                    result.lengthIn(),
                    result.widthIn(),
                    result.heightIn(),
                    result.actualWeightLb(),
                    result.volumetricWeightLb(),
                    result.billableWeightLb(),
                    result.totalVolumeCuIn(),
                    packing);
        }
    }
}

package com.invsys.api;

import com.invsys.domain.Shipment;
import com.invsys.repository.ShipmentRepository;
import com.invsys.service.ShipmentService;
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

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public Shipment create(@Valid @RequestBody CreateShipmentRequest request) {
        List<ShipmentService.ShipLineRequest> lines = request.lines().stream()
                .map(l -> new ShipmentService.ShipLineRequest(l.salesOrderLineId(), l.quantity()))
                .toList();
        return shipmentService.createShipment(request.salesOrderId(), request.carrier(),
                request.trackingNumber(), lines);
    }

    @PostMapping("/pack-label")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public Shipment createPackLabel(@Valid @RequestBody PackLabelRequest request) {
        return shipmentService.createPackLabel(
                request.salesOrderId(), request.totalWeightLb(), request.carrier());
    }

    public record PackLabelRequest(
            @NotNull UUID salesOrderId,
            @NotNull BigDecimal totalWeightLb,
            String carrier
    ) {
    }

    public record CreateShipmentRequest(
            @NotNull UUID salesOrderId,
            String carrier,
            String trackingNumber,
            List<ShipLineRequest> lines
    ) {
    }

    public record ShipLineRequest(@NotNull UUID salesOrderLineId, @NotNull BigDecimal quantity) {
    }
}

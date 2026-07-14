package com.invsys.api;

import com.invsys.api.dto.VanStockLevelResponse;
import com.invsys.api.dto.VehicleAssignmentResponse;
import com.invsys.service.FieldFulfillmentService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/field")
public class FieldController {

    private final FieldFulfillmentService fieldService;

    public FieldController(FieldFulfillmentService fieldService) {
        this.fieldService = fieldService;
    }

    @PostMapping("/vehicles/assign")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public VehicleAssignmentResponse assign(@Valid @RequestBody AssignVehicleRequest request) {
        return fieldService.assignVehicle(request.locationId(), request.technicianUserId());
    }

    @PostMapping("/vehicles/{assignmentId}/return")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public VehicleAssignmentResponse returnVehicle(@PathVariable UUID assignmentId) {
        return fieldService.returnVehicle(assignmentId);
    }

    @GetMapping("/vehicles/active")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public VehicleAssignmentResponse active() {
        UUID userId = TenantContext.getUserId()
                .orElseThrow();
        return fieldService.activeAssignmentForUser(userId);
    }

    @PostMapping("/van/replenish")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<UUID> replenish(@Valid @RequestBody ReplenishRequest request) {
        Map<UUID, BigDecimal> items = request.items().stream()
                .collect(Collectors.toMap(ReplenishItem::variantId, ReplenishItem::quantity, BigDecimal::add));
        return fieldService.replenishVan(request.fromWarehouseId(), request.toVehicleLocationId(), items);
    }

    @PostMapping("/van/consume")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public void consume(@Valid @RequestBody ConsumeRequest request) {
        fieldService.consumeFromVan(request.variantId(), request.quantity(), request.reason());
    }

    @GetMapping("/van/stock")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public List<VanStockLevelResponse> stock(@RequestParam UUID locationId) {
        return fieldService.vanStock(locationId);
    }

    public record AssignVehicleRequest(
            @NotNull UUID locationId,
            @NotNull UUID technicianUserId
    ) {
    }

    public record ReplenishRequest(
            @NotNull UUID fromWarehouseId,
            @NotNull UUID toVehicleLocationId,
            @NotEmpty List<ReplenishItem> items
    ) {
    }

    public record ReplenishItem(
            @NotNull UUID variantId,
            @NotNull @Positive BigDecimal quantity
    ) {
    }

    public record ConsumeRequest(
            @NotNull UUID variantId,
            @NotNull @Positive BigDecimal quantity,
            String reason
    ) {
    }
}

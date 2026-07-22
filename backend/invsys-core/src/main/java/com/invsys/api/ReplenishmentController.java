package com.invsys.api;

import com.invsys.api.dto.ReplenishmentTaskDto;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.ReplenishmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse/replenishments")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;
    private final InventoryService inventoryService;

    public ReplenishmentController(ReplenishmentService replenishmentService,
                                   InventoryService inventoryService) {
        this.replenishmentService = replenishmentService;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<ReplenishmentTaskDto> list() {
        return replenishmentService.listSuggestedTransfers();
    }

    /**
     * Confirm a physical RESERVE → PICK_FACE move (append-only TRANSFER_OUT/IN ledger pair).
     */
    @PostMapping("/confirm")
    public Map<String, UUID> confirm(@Valid @RequestBody ConfirmReplenishmentRequest request) {
        UUID groupId = inventoryService.transfer(
                request.variantId(),
                request.fromLocationId(),
                request.toLocationId(),
                request.lotId(),
                request.quantity());
        return Map.of("transferGroupId", groupId);
    }

    public record ConfirmReplenishmentRequest(
            @NotNull UUID variantId,
            @NotNull UUID fromLocationId,
            @NotNull UUID toLocationId,
            UUID lotId,
            @NotNull BigDecimal quantity
    ) {
    }
}

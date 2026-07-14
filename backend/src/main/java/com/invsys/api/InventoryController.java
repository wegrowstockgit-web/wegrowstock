package com.invsys.api;

import com.invsys.domain.InventoryLedger;
import com.invsys.domain.InventoryLevel;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
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
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryLevelRepository levelRepository;

    public InventoryController(InventoryService inventoryService, InventoryLevelRepository levelRepository) {
        this.inventoryService = inventoryService;
        this.levelRepository = levelRepository;
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<InventoryLevel> levels(@RequestParam(required = false) UUID variantId) {
        if (variantId != null) {
            return levelRepository.findByTenantIdAndVariantId(TenantContext.requireTenantId(), variantId);
        }
        return levelRepository.findAll();
    }

    @PostMapping("/receive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public InventoryLedger receive(@Valid @RequestBody ReceiveRequest request) {
        return inventoryService.receive(request.variantId(), request.locationId(), request.lotId(),
                request.quantity(), request.referenceType(), request.referenceId());
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public InventoryLedger adjust(@Valid @RequestBody AdjustRequest request) {
        return inventoryService.adjust(request.variantId(), request.locationId(), request.lotId(),
                request.delta(), request.reasonCode());
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public UUID transfer(@Valid @RequestBody TransferRequest request) {
        return inventoryService.transfer(request.variantId(), request.fromLocationId(), request.toLocationId(),
                request.lotId(), request.quantity());
    }

    public record ReceiveRequest(
            @NotNull UUID variantId,
            @NotNull UUID locationId,
            UUID lotId,
            @NotNull BigDecimal quantity,
            String referenceType,
            UUID referenceId
    ) {
    }

    public record AdjustRequest(
            @NotNull UUID variantId,
            @NotNull UUID locationId,
            UUID lotId,
            @NotNull BigDecimal delta,
            String reasonCode
    ) {
    }

    public record TransferRequest(
            @NotNull UUID variantId,
            @NotNull UUID fromLocationId,
            @NotNull UUID toLocationId,
            UUID lotId,
            @NotNull BigDecimal quantity
    ) {
    }
}

package com.invsys.modules.fulfillment.api;

import com.invsys.service.CrossDockService;
import com.invsys.service.PickingWaveService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cross-dock analysis for wave planning (Surface A).
 */
@RestController
@RequestMapping("/api/v1/picking")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class PickingWaveController {

    private final PickingWaveService pickingWaveService;

    public PickingWaveController(PickingWaveService pickingWaveService) {
        this.pickingWaveService = pickingWaveService;
    }

    @GetMapping("/cross-dock/suggestions")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<CrossDockSuggestionResponse> crossDockSuggestions() {
        return pickingWaveService.crossDockSuggestions().stream()
                .map(s -> new CrossDockSuggestionResponse(
                        s.variantId(),
                        s.sku(),
                        s.salesOrderId(),
                        s.salesOrderNumber(),
                        s.salesOrderLineId(),
                        s.purchaseOrderId(),
                        s.purchaseOrderNumber(),
                        s.purchaseOrderLineId(),
                        s.suggestedQty(),
                        s.salesOpenQty(),
                        s.inboundOpenQty()))
                .toList();
    }

    public record CrossDockSuggestionResponse(
            UUID variantId,
            String sku,
            UUID salesOrderId,
            String salesOrderNumber,
            UUID salesOrderLineId,
            UUID purchaseOrderId,
            String purchaseOrderNumber,
            UUID purchaseOrderLineId,
            BigDecimal suggestedQty,
            BigDecimal salesOpenQty,
            BigDecimal inboundOpenQty
    ) {
    }
}

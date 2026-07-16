package com.invsys.api;

import com.invsys.domain.PurchaseOrderLine;
import com.invsys.service.LandedCostService;
import com.invsys.service.PurchaseOrderService;
import com.invsys.service.landedcost.HybridLandedCostEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchasing")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class LandedCostController {

    private final LandedCostService landedCostService;
    private final PurchaseOrderService purchaseOrderService;

    public LandedCostController(LandedCostService landedCostService,
                                PurchaseOrderService purchaseOrderService) {
        this.landedCostService = landedCostService;
        this.purchaseOrderService = purchaseOrderService;
    }

    /**
     * Receive PO lines with optional freight/customs surcharge folded into RECEIVE unit_cost
     * for moving-average COGS accuracy.
     */
    @PostMapping("/receive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<PurchaseOrderLine> receive(@Valid @RequestBody PurchasingReceiveRequest request) {
        List<PurchaseOrderService.ReceiveLineInput> lines = request.lines().stream()
                .map(l -> new PurchaseOrderService.ReceiveLineInput(l.lineId(), l.quantity(), l.lotId()))
                .toList();
        return purchaseOrderService.receiveWithLandedCost(
                request.purchaseOrderId(),
                request.locationId(),
                request.landedCostSurcharge(),
                lines);
    }

    @PostMapping("/invoices/{id}/landed-costs")
    public LandedCostResponse allocate(@PathVariable UUID id, @Valid @RequestBody LandedCostRequest request) {
        HybridLandedCostEngine.CostEventType eventType = parseEventType(request);
        String strategy = request.strategy() != null ? request.strategy() : defaultStrategy(eventType);
        BigDecimal total = request.freightTotal() != null ? request.freightTotal() : request.totalCost();
        if (total == null) {
            total = BigDecimal.ZERO;
        }

        LandedCostService.LandedCostResult result = landedCostService.allocate(id, total, eventType, strategy);
        return new LandedCostResponse(
                result.allocationId(),
                result.invoiceId(),
                result.purchaseOrderId(),
                result.freightTotal(),
                eventType.name(),
                result.strategy(),
                result.lines());
    }

    private static HybridLandedCostEngine.CostEventType parseEventType(LandedCostRequest request) {
        if (request.eventType() != null && !request.eventType().isBlank()) {
            return HybridLandedCostEngine.CostEventType.valueOf(request.eventType().trim().toUpperCase());
        }
        // Legacy: BY_VALUE implies customs
        if (request.strategy() != null
                && ("BY_VALUE".equalsIgnoreCase(request.strategy())
                || "VALUE".equalsIgnoreCase(request.strategy())
                || "CUSTOMS".equalsIgnoreCase(request.strategy()))) {
            return HybridLandedCostEngine.CostEventType.CUSTOMS_DUTY;
        }
        return HybridLandedCostEngine.CostEventType.FREIGHT;
    }

    private static String defaultStrategy(HybridLandedCostEngine.CostEventType eventType) {
        return eventType == HybridLandedCostEngine.CostEventType.CUSTOMS_DUTY ? "CUSTOMS" : "HYBRID";
    }

    public record LandedCostRequest(
            @PositiveOrZero BigDecimal freightTotal,
            @PositiveOrZero BigDecimal totalCost,
            String eventType,
            String strategy
    ) {
    }

    public record LandedCostResponse(
            UUID allocationId,
            UUID invoiceId,
            UUID purchaseOrderId,
            BigDecimal freightTotal,
            String eventType,
            String strategy,
            List<Map<String, Object>> lines
    ) {
    }

    public record PurchasingReceiveRequest(
            @NotNull UUID purchaseOrderId,
            @NotNull UUID locationId,
            @PositiveOrZero BigDecimal landedCostSurcharge,
            @NotEmpty List<PurchasingReceiveLine> lines
    ) {
    }

    public record PurchasingReceiveLine(
            @NotNull UUID lineId,
            @NotNull @Positive BigDecimal quantity,
            UUID lotId
    ) {
    }
}

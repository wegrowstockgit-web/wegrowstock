package com.invsys.api;

import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.service.MrpCalculationEngine;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchasing/mrp")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class MrpController {

    private final MrpCalculationEngine mrpCalculationEngine;

    public MrpController(MrpCalculationEngine mrpCalculationEngine) {
        this.mrpCalculationEngine = mrpCalculationEngine;
    }

    @PostMapping("/calculate")
    public MrpCalculateResponse calculate() {
        MrpCalculationEngine.MrpRunResult result = mrpCalculationEngine.calculateAndCreateDraftPos();
        return new MrpCalculateResponse(
                result.createdPurchaseOrders().stream()
                        .map(po -> new CreatedPoResponse(po.getId(), po.getNumber(), po.getSupplierId()))
                        .toList(),
                result.suggestions().stream().map(MrpSuggestionResponse::from).toList());
    }

    @GetMapping("/suggestions")
    public List<MrpSuggestionResponse> suggestions() {
        return mrpCalculationEngine.calculateSuggestions().stream()
                .map(MrpSuggestionResponse::from)
                .toList();
    }

    public record MrpCalculateResponse(
            List<CreatedPoResponse> createdPurchaseOrders,
            List<MrpSuggestionResponse> suggestions
    ) {
    }

    public record CreatedPoResponse(UUID id, String number, UUID supplierId) {
    }

    public record MrpSuggestionResponse(
            UUID variantId,
            String sku,
            java.math.BigDecimal openSalesQty,
            java.math.BigDecimal safetyStock,
            java.math.BigDecimal onHand,
            java.math.BigDecimal allocated,
            java.math.BigDecimal inboundOpenPoQty,
            java.math.BigDecimal netRequirement,
            java.math.BigDecimal suggestedOrderQty,
            UUID defaultSupplierId,
            String defaultSupplierName,
            int leadTimeDays,
            java.math.BigDecimal unitCost,
            java.math.BigDecimal capitalEstimate
    ) {
        static MrpSuggestionResponse from(MrpCalculationEngine.MrpSuggestion suggestion) {
            return new MrpSuggestionResponse(
                    suggestion.variantId(),
                    suggestion.sku(),
                    suggestion.openSalesQty(),
                    suggestion.safetyStock(),
                    suggestion.onHand(),
                    suggestion.allocated(),
                    suggestion.inboundOpenPoQty(),
                    suggestion.netRequirement(),
                    suggestion.suggestedOrderQty(),
                    suggestion.defaultSupplierId(),
                    suggestion.defaultSupplierName(),
                    suggestion.leadTimeDays(),
                    suggestion.unitCost(),
                    suggestion.capitalEstimate());
        }
    }
}

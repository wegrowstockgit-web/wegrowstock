package com.invsys.api;

import com.invsys.core.common.OffsetPaging;
import com.invsys.core.common.PageResponse;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.service.MrpCalculationEngine;
import com.invsys.service.MrpConsolidateJobService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchasing/mrp")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class MrpController {

    private static final Set<String> SUGGESTION_SORT = Set.of(
            "sku", "supplier", "qty", "suggestedOrderQty", "lead", "leadTimeDays",
            "capital", "capitalEstimate", "onHand", "allocated", "inbound", "inboundOpenPoQty");

    private final MrpCalculationEngine mrpCalculationEngine;
    private final MrpConsolidateJobService consolidateJobService;

    public MrpController(MrpCalculationEngine mrpCalculationEngine,
                         MrpConsolidateJobService consolidateJobService) {
        this.mrpCalculationEngine = mrpCalculationEngine;
        this.consolidateJobService = consolidateJobService;
    }

    @PostMapping("/calculate")
    public MrpCalculateResponse calculate(@RequestBody(required = false) MrpCalculateRequest request) {
        MrpCalculationEngine.MrpRunResult result =
                mrpCalculationEngine.calculateAndCreateDraftPos(toCommand(request));
        return toCalculateResponse(result);
    }

    @PostMapping("/calculate/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MrpJobResponse enqueue(@RequestBody(required = false) MrpCalculateRequest request) {
        return toJobResponse(consolidateJobService.enqueue(toCommand(request)));
    }

    @GetMapping("/calculate/jobs/{jobId}")
    public MrpJobResponse job(@PathVariable UUID jobId) {
        return toJobResponse(consolidateJobService.requireJob(jobId));
    }

    @GetMapping("/suggestions")
    public PageResponse<MrpSuggestionResponse> suggestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "capital,desc") String sort,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String urgency) {
        Page<MrpCalculationEngine.MrpSuggestion> result = mrpCalculationEngine.pageSuggestions(
                OffsetPaging.of(page, size, sort, "capital", org.springframework.data.domain.Sort.Direction.DESC,
                        SUGGESTION_SORT),
                OffsetPaging.keyword(search),
                supplierId,
                urgency);
        return PageResponse.of(result, result.getContent().stream().map(MrpSuggestionResponse::from).toList());
    }

    @GetMapping("/suggestions/summary")
    public MrpSummaryResponse summary(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String urgency) {
        MrpCalculationEngine.MrpSuggestionSummary summary =
                mrpCalculationEngine.summarizeSuggestions(OffsetPaging.keyword(search), supplierId, urgency);
        return new MrpSummaryResponse(summary.qualifyingLineCount(), summary.totalCapital());
    }

    private static MrpCalculationEngine.MrpCalculateCommand toCommand(MrpCalculateRequest request) {
        if (request == null) {
            return new MrpCalculationEngine.MrpCalculateCommand(Map.of(), null, null);
        }
        Map<UUID, BigDecimal> overrides = new LinkedHashMap<>();
        if (request.overrides() != null) {
            for (MrpQtyOverride override : request.overrides()) {
                if (override == null || override.variantId() == null || override.suggestedOrderQty() == null) {
                    continue;
                }
                overrides.put(override.variantId(), override.suggestedOrderQty());
            }
        }
        return new MrpCalculationEngine.MrpCalculateCommand(overrides, request.supplierId(), request.urgency());
    }

    private static MrpCalculateResponse toCalculateResponse(MrpCalculationEngine.MrpRunResult result) {
        return new MrpCalculateResponse(
                result.createdPurchaseOrders().stream()
                        .map(MrpController::toCreatedPo)
                        .toList(),
                result.suggestions().stream().map(MrpSuggestionResponse::from).toList());
    }

    private static CreatedPoResponse toCreatedPo(PurchaseOrder po) {
        return new CreatedPoResponse(po.getId(), po.getNumber(), po.getSupplierId());
    }

    private static MrpJobResponse toJobResponse(MrpConsolidateJobService.Job job) {
        MrpCalculateResponse result = job.result() == null ? null : toCalculateResponse(job.result());
        return new MrpJobResponse(job.jobId(), job.status(), job.error(), result);
    }

    public record MrpCalculateRequest(
            List<MrpQtyOverride> overrides,
            UUID supplierId,
            String urgency
    ) {
    }

    public record MrpQtyOverride(UUID variantId, BigDecimal suggestedOrderQty) {
    }

    public record MrpCalculateResponse(
            List<CreatedPoResponse> createdPurchaseOrders,
            List<MrpSuggestionResponse> suggestions
    ) {
    }

    public record MrpJobResponse(
            UUID jobId,
            String status,
            String error,
            MrpCalculateResponse result
    ) {
    }

    public record MrpSummaryResponse(long qualifyingLineCount, java.math.BigDecimal totalCapital) {
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
            java.math.BigDecimal minStock,
            java.math.BigDecimal maxStock,
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
                    suggestion.minStock(),
                    suggestion.maxStock(),
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

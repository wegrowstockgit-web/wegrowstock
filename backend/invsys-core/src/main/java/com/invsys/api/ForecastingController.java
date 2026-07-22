package com.invsys.api;

import com.invsys.domain.DemandForecast;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.service.ForecastingInferenceService;
import com.invsys.service.ForecastingService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/forecasting")
public class ForecastingController {

    private final ForecastingService forecastingService;
    private final ForecastingInferenceService inferenceService;
    private final DemandForecastRepository forecastRepository;
    private final ProductVariantRepository variantRepository;

    public ForecastingController(ForecastingService forecastingService,
                                   ForecastingInferenceService inferenceService,
                                   DemandForecastRepository forecastRepository,
                                   ProductVariantRepository variantRepository) {
        this.forecastingService = forecastingService;
        this.inferenceService = inferenceService;
        this.forecastRepository = forecastRepository;
        this.variantRepository = variantRepository;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<ForecastingService.ForecastAlert> alerts() {
        return forecastingService.alerts();
    }

    @PostMapping("/draft-po")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<DraftPoResponse> createDraftPo(@Valid @RequestBody DraftPoRequest request) {
        return forecastingService.createDraftPo(request.variantIds()).stream()
                .map(po -> new DraftPoResponse(po.getId(), po.getNumber(), po.getSupplierId()))
                .toList();
    }

    @GetMapping("/chart-data")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<DemandChartPointResponse> chartData() {
        UUID tenantId = TenantContext.requireTenantId();
        var skuByVariant = variantRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.invsys.modules.catalog.domain.ProductVariant::getId,
                        com.invsys.modules.catalog.domain.ProductVariant::getSku,
                        (a, b) -> a));
        return inferenceService.chartSeries(forecastRepository.findByTenantIdOrderByRecommendedPoQtyDesc(tenantId))
                .stream()
                .map(p -> new DemandChartPointResponse(
                        p.variantId(),
                        skuByVariant.getOrDefault(p.variantId(), "—"),
                        p.historicalVelocity(),
                        p.forecastQty(),
                        p.seasonalityIndex(),
                        p.confidenceScore(),
                        p.calculatedAt()))
                .toList();
    }

    public record DraftPoRequest(@NotEmpty List<UUID> variantIds) {
    }

    public record DraftPoResponse(UUID id, String number, UUID supplierId) {
    }

    public record DemandChartPointResponse(
            UUID variantId,
            String sku,
            java.math.BigDecimal historicalVelocity,
            java.math.BigDecimal forecastQty,
            java.math.BigDecimal seasonalityIndex,
            java.math.BigDecimal confidenceScore,
            java.time.Instant calculatedAt
    ) {
    }
}

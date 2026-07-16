package com.invsys.api;

import com.invsys.repository.AnalyticsRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AnalyticsController {

    private final AnalyticsRepository analyticsRepository;
    private final TenantSettingsRepository settingsRepository;

    public AnalyticsController(AnalyticsRepository analyticsRepository,
                               TenantSettingsRepository settingsRepository) {
        this.analyticsRepository = analyticsRepository;
        this.settingsRepository = settingsRepository;
    }

    @GetMapping("/valuation")
    public ValuationResponse valuation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOfDate) {
        AnalyticsRepository.ValuationSnapshot snap = analyticsRepository.valuationAsOf(asOfDate);
        return toResponse(snap);
    }

    @GetMapping("/valuation/history")
    public ValuationHistoryResponse valuationHistory(
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "30") int points) {
        Instant to = Instant.now();
        Instant from = to.minus(Math.max(1, days), ChronoUnit.DAYS);
        List<AnalyticsRepository.ValuationSnapshot> snaps =
                analyticsRepository.valuationHistory(from, to, points);
        List<ValuationHistoryPoint> series = snaps.stream()
                .map(s -> new ValuationHistoryPoint(
                        s.asOfDate(),
                        s.totalValue().setScale(4, RoundingMode.HALF_UP)))
                .toList();
        return new ValuationHistoryResponse(resolveCurrency(), series);
    }

    private ValuationResponse toResponse(AnalyticsRepository.ValuationSnapshot snap) {
        List<ValuationLineDto> lines = snap.lines().stream()
                .map(l -> new ValuationLineDto(
                        l.variantId(),
                        l.locationId(),
                        l.quantityOnHand(),
                        l.totalValue().setScale(4, RoundingMode.HALF_UP)))
                .toList();
        return new ValuationResponse(
                snap.asOfDate(),
                snap.totalValue().setScale(4, RoundingMode.HALF_UP),
                resolveCurrency(),
                lines);
    }

    private String resolveCurrency() {
        UUID tenantId = TenantContext.requireTenantId();
        return settingsRepository.findByTenantId(tenantId)
                .map(s -> s.getSettings())
                .map(m -> m.get("currency"))
                .map(String::valueOf)
                .filter(c -> c != null && !c.isBlank() && !"null".equalsIgnoreCase(c))
                .orElse("USD");
    }

    public record ValuationLineDto(
            UUID variantId,
            UUID locationId,
            BigDecimal quantityOnHand,
            BigDecimal totalValue
    ) {
    }

    public record ValuationResponse(
            Instant asOfDate,
            BigDecimal totalValue,
            String currency,
            List<ValuationLineDto> lines
    ) {
    }

    public record ValuationHistoryPoint(Instant asOfDate, BigDecimal totalValue) {
    }

    public record ValuationHistoryResponse(String currency, List<ValuationHistoryPoint> points) {
    }
}

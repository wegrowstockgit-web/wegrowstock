package com.invsys.api;

import com.invsys.service.LaborAnalyticsService;
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
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class LaborAnalyticsController {

    private final LaborAnalyticsService laborAnalyticsService;

    public LaborAnalyticsController(LaborAnalyticsService laborAnalyticsService) {
        this.laborAnalyticsService = laborAnalyticsService;
    }

    @GetMapping("/labor-velocity")
    public LaborVelocityResponse laborVelocity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant start = from != null ? from : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant end = to != null ? to : Instant.now();
        List<LaborAnalyticsService.OperatorVelocity> rows =
                laborAnalyticsService.calculateOperatorVelocity(tenantId, start, end);

        BigDecimal avgActive = rows.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : rows.stream()
                        .map(LaborAnalyticsService.OperatorVelocity::activePph)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);

        List<OperatorVelocityDto> operators = rows.stream()
                .map(row -> OperatorVelocityDto.from(row, avgActive))
                .toList();

        return new LaborVelocityResponse(start, end, avgActive, operators);
    }

    public record HourlyPickDto(String hour, long picks) {
        static HourlyPickDto from(LaborAnalyticsService.HourlyPoint point) {
            return new HourlyPickDto(point.label(), point.picks());
        }
    }

    public record OperatorVelocityDto(
            UUID userId,
            String operatorName,
            long totalPicks,
            long totalReceives,
            BigDecimal activePph,
            BigDecimal shiftPph,
            BigDecimal utilizationPercent,
            BigDecimal activeWaveHours,
            BigDecimal shiftHours,
            BigDecimal activePphDeltaVsAvg,
            List<HourlyPickDto> hourlyPicks
    ) {
        static OperatorVelocityDto from(LaborAnalyticsService.OperatorVelocity row, BigDecimal avgActive) {
            BigDecimal delta = row.activePph().subtract(avgActive).setScale(2, RoundingMode.HALF_UP);
            return new OperatorVelocityDto(
                    row.userId(),
                    row.operatorName(),
                    row.totalPicks(),
                    row.totalReceives(),
                    row.activePph(),
                    row.shiftPph(),
                    row.utilizationPercent(),
                    row.activeWaveHours(),
                    row.shiftHours(),
                    delta,
                    row.hourlyPicks().stream().map(HourlyPickDto::from).toList());
        }
    }

    public record LaborVelocityResponse(
            Instant from,
            Instant to,
            BigDecimal warehouseAvgActivePph,
            List<OperatorVelocityDto> operators
    ) {
    }
}

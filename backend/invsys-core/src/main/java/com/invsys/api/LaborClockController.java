package com.invsys.api;

import com.invsys.service.LaborClockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labor")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class LaborClockController {

    private final LaborClockService laborClockService;

    public LaborClockController(LaborClockService laborClockService) {
        this.laborClockService = laborClockService;
    }

    @PostMapping("/clock-in")
    public StatusResponse clockIn(@Valid @RequestBody(required = false) ClockInBody body) {
        UUID warehouseId = body != null ? body.warehouseId() : null;
        return toStatusResponse(laborClockService.clockIn(warehouseId));
    }

    @PostMapping("/clock-out")
    public StatusResponse clockOut() {
        return toStatusResponse(laborClockService.clockOut());
    }

    @PostMapping("/switch-activity")
    public StatusResponse switchActivity(@Valid @RequestBody SwitchActivityBody body) {
        return toStatusResponse(laborClockService.switchActivity(body.activityType()));
    }

    @GetMapping("/me")
    public StatusResponse currentStatus() {
        return toStatusResponse(laborClockService.currentStatus());
    }

    @GetMapping("/analytics")
    public AnalyticsResponse analytics() {
        LaborClockService.AnalyticsSummary summary = laborClockService.analyticsSummary();
        return new AnalyticsResponse(
                summary.unitsPerHour(),
                summary.directHours(),
                summary.indirectHours(),
                summary.directPercent(),
                summary.indirectPercent());
    }

    private StatusResponse toStatusResponse(LaborClockService.LaborStatus status) {
        return new StatusResponse(
                status.shiftId(),
                status.warehouseId(),
                status.clockIn(),
                status.clockOut(),
                status.currentActivity(),
                status.active());
    }

    public record ClockInBody(UUID warehouseId) {
    }

    public record SwitchActivityBody(@NotBlank String activityType) {
    }

    public record StatusResponse(
            UUID shiftId,
            UUID warehouseId,
            Instant clockIn,
            Instant clockOut,
            String currentActivity,
            boolean active
    ) {
    }

    public record AnalyticsResponse(
            BigDecimal unitsPerHour,
            BigDecimal directHours,
            BigDecimal indirectHours,
            BigDecimal directPercent,
            BigDecimal indirectPercent
    ) {
    }
}

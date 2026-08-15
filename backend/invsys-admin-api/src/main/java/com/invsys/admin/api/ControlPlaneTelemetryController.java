package com.invsys.admin.api;

import com.invsys.admin.audit.PlatformAudit;
import com.invsys.admin.service.AdminTelemetryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/telemetry")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneTelemetryController {

    private final AdminTelemetryService adminTelemetryService;

    public ControlPlaneTelemetryController(AdminTelemetryService adminTelemetryService) {
        this.adminTelemetryService = adminTelemetryService;
    }

    @GetMapping("/tenants")
    public List<AdminTelemetryService.TenantTelemetryView> listTenants() {
        return adminTelemetryService.listTenants();
    }

    @PutMapping("/tenants/{tenantId}/rate-limit")
    @PlatformAudit(action = "TENANT_RATE_LIMIT", tenantIdParam = "tenantId")
    public AdminTelemetryService.TenantTelemetryView setRateLimit(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RateLimitRequest request) {
        return adminTelemetryService.setRateLimit(tenantId, request.capacityMultiplier());
    }

    public record RateLimitRequest(
            @NotNull Double capacityMultiplier
    ) {
    }
}

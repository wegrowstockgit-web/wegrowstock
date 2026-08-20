package com.invsys.admin.api;

import com.invsys.admin.audit.PlatformAudit;
import com.invsys.core.service.FeatureFlagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/flags")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneFeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public ControlPlaneFeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    public List<FeatureFlagService.FlagView> list() {
        return featureFlagService.listFlags();
    }

    @PostMapping
    @PlatformAudit(action = "FEATURE_FLAG_CREATE")
    public FeatureFlagService.FlagView create(@Valid @RequestBody CreateFlagRequest request) {
        return featureFlagService.createFlag(request.flagKey(), request.description(),
                Boolean.TRUE.equals(request.isGlobal()));
    }

    @PutMapping("/{id}/tenants")
    @PlatformAudit(action = "FEATURE_FLAG_TENANTS")
    public FeatureFlagService.FlagView replaceTenants(
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceTenantsRequest request) {
        List<FeatureFlagService.TenantOverrideView> overrides = request.overrides() == null
                ? List.of()
                : request.overrides().stream()
                .map(row -> new FeatureFlagService.TenantOverrideView(row.tenantId(), row.enabled()))
                .toList();
        return featureFlagService.replaceTenantOverrides(id, request.isGlobal(), overrides);
    }

    public record CreateFlagRequest(
            @NotBlank @Size(max = 64) String flagKey,
            String description,
            Boolean isGlobal
    ) {
    }

    public record ReplaceTenantsRequest(
            Boolean isGlobal,
            List<TenantOverrideRequest> overrides
    ) {
    }

    public record TenantOverrideRequest(UUID tenantId, boolean enabled) {
    }
}

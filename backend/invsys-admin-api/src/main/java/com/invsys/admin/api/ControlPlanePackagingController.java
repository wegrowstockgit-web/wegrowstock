package com.invsys.admin.api;

import com.invsys.admin.audit.PlatformAudit;
import com.invsys.domain.subscription.AppModule;
import com.invsys.service.TenantSubscriptionService;
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

@RestController
@RequestMapping("/api/v1/control-plane/packaging/tiers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlanePackagingController {

    private final TenantSubscriptionService tenantSubscriptionService;

    public ControlPlanePackagingController(TenantSubscriptionService tenantSubscriptionService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
    }

    @GetMapping
    public List<TenantSubscriptionService.PlatformTierDefinitionView> listTiers() {
        return tenantSubscriptionService.listTierDefinitions();
    }

    @PutMapping("/{tierCode}")
    @PlatformAudit(action = "TIER_PACKAGING_REPLACE")
    public TenantSubscriptionService.PlatformTierDefinitionView replaceTierModules(
            @PathVariable String tierCode,
            @Valid @RequestBody ReplaceTierModulesRequest request) {
        return tenantSubscriptionService.replaceTierDefinition(tierCode, request.defaultModules());
    }

    public record ReplaceTierModulesRequest(
            @NotNull List<AppModule> defaultModules
    ) {
    }
}

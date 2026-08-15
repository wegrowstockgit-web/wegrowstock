package com.invsys.admin.api;

import com.invsys.admin.audit.PlatformAudit;
import com.invsys.admin.service.AdminImpersonationService;
import com.invsys.admin.service.AdminSandboxProvisioningService;
import com.invsys.admin.service.AdminTenantLifecycleService;
import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import com.invsys.service.TenantSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneTenantController {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final AdminImpersonationService adminImpersonationService;
    private final AdminTenantLifecycleService adminTenantLifecycleService;
    private final AdminSandboxProvisioningService adminSandboxProvisioningService;

    public ControlPlaneTenantController(TenantSubscriptionService tenantSubscriptionService,
                                        AdminImpersonationService adminImpersonationService,
                                        AdminTenantLifecycleService adminTenantLifecycleService,
                                        AdminSandboxProvisioningService adminSandboxProvisioningService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.adminImpersonationService = adminImpersonationService;
        this.adminTenantLifecycleService = adminTenantLifecycleService;
        this.adminSandboxProvisioningService = adminSandboxProvisioningService;
    }

    @GetMapping
    public List<TenantSubscriptionService.ControlPlaneTenantView> listTenants() {
        return tenantSubscriptionService.listTenantsWithModules();
    }

    @PatchMapping("/{tenantId}/modules")
    @PlatformAudit(action = "TENANT_MODULES_REPLACE", tenantIdParam = "tenantId")
    public TenantSubscriptionService.ControlPlaneTenantView replaceModules(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ReplaceModulesRequest request) {
        return tenantSubscriptionService.replaceEnabledModules(tenantId, request.enabledModules());
    }

    @PatchMapping("/{tenantId}/tier")
    @PlatformAudit(action = "TENANT_TIER_REPLACE", tenantIdParam = "tenantId")
    public TenantSubscriptionService.ControlPlaneTenantView replaceTier(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ReplaceTierRequest request) {
        return tenantSubscriptionService.replaceTier(tenantId, request.tier());
    }

    @PostMapping("/{tenantId}/impersonate")
    public AdminImpersonationService.ImpersonationResponse impersonate(@PathVariable UUID tenantId) {
        return adminImpersonationService.impersonate(tenantId);
    }

    @PatchMapping("/{tenantId}/status")
    @PlatformAudit(action = "TENANT_STATUS_UPDATE", tenantIdParam = "tenantId")
    public AdminTenantLifecycleService.TenantStatusView updateStatus(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateStatusRequest request) {
        return adminTenantLifecycleService.updateStatus(tenantId, request.status());
    }

    @PostMapping("/{tenantId}/clone-sandbox")
    public AdminSandboxProvisioningService.SandboxCredentials cloneSandbox(@PathVariable UUID tenantId) {
        return adminSandboxProvisioningService.cloneSandbox(tenantId);
    }

    public record ReplaceModulesRequest(
            @NotNull List<AppModule> enabledModules
    ) {
    }

    public record ReplaceTierRequest(
            @NotNull CommercialTier tier
    ) {
    }

    public record UpdateStatusRequest(
            @NotBlank String status
    ) {
    }
}


package com.invsys.admin.api;

import com.invsys.admin.audit.PlatformAudit;
import com.invsys.admin.service.AdminIntegrationOpsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/integrations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneIntegrationController {

    private final AdminIntegrationOpsService adminIntegrationOpsService;

    public ControlPlaneIntegrationController(AdminIntegrationOpsService adminIntegrationOpsService) {
        this.adminIntegrationOpsService = adminIntegrationOpsService;
    }

    @GetMapping("/traffic")
    public List<AdminIntegrationOpsService.TrafficRow> traffic() {
        return adminIntegrationOpsService.trafficLast24h();
    }

    @PostMapping("/tenants/{tenantId}/kill-switch")
    @PlatformAudit(action = "INTEGRATION_KILL_SWITCH", tenantIdParam = "tenantId")
    public AdminIntegrationOpsService.KillSwitchView killSwitch(
            @PathVariable UUID tenantId,
            @Valid @RequestBody KillSwitchRequest request) {
        return adminIntegrationOpsService.setKillSwitch(tenantId, request.paused(), request.reason());
    }

    public record KillSwitchRequest(
            @NotNull Boolean paused,
            String reason
    ) {
    }
}

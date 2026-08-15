package com.invsys.admin.api;

import com.invsys.admin.service.AdminComplianceBroadcastService;
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
@RequestMapping("/api/v1/control-plane/compliance/broadcasts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneComplianceController {

    private final AdminComplianceBroadcastService adminComplianceBroadcastService;

    public ControlPlaneComplianceController(AdminComplianceBroadcastService adminComplianceBroadcastService) {
        this.adminComplianceBroadcastService = adminComplianceBroadcastService;
    }

    @GetMapping
    public List<AdminComplianceBroadcastService.BroadcastView> list() {
        return adminComplianceBroadcastService.list();
    }

    @PostMapping
    public AdminComplianceBroadcastService.BroadcastView create(
            @RequestBody AdminComplianceBroadcastService.CreateBroadcastRequest request) {
        return adminComplianceBroadcastService.create(request);
    }

    @PostMapping("/{id}/activate")
    public AdminComplianceBroadcastService.BroadcastView activate(@PathVariable UUID id) {
        return adminComplianceBroadcastService.activate(id);
    }
}

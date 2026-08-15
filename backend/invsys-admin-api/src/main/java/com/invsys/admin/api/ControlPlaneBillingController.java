package com.invsys.admin.api;

import com.invsys.admin.service.AdminPlatformBillingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/control-plane/billing")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneBillingController {

    private final AdminPlatformBillingService adminPlatformBillingService;

    public ControlPlaneBillingController(AdminPlatformBillingService adminPlatformBillingService) {
        this.adminPlatformBillingService = adminPlatformBillingService;
    }

    @GetMapping("/overview")
    public AdminPlatformBillingService.BillingOverview overview() {
        return adminPlatformBillingService.overview();
    }
}

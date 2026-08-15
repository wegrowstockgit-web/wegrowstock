package com.invsys.admin.api;

import com.invsys.admin.service.AdminReportingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/control-plane/reports")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneReportsController {

    private final AdminReportingService adminReportingService;

    public ControlPlaneReportsController(AdminReportingService adminReportingService) {
        this.adminReportingService = adminReportingService;
    }

    @GetMapping("/commercial")
    public AdminReportingService.CommercialReport commercialReport() {
        return adminReportingService.commercialReport();
    }

    @GetMapping("/health")
    public AdminReportingService.HealthReport healthReport() {
        return adminReportingService.healthReport();
    }
}

package com.invsys.admin.api;

import com.invsys.admin.service.PlatformAuditQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/control-plane/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneAuditController {

    private final PlatformAuditQueryService platformAuditQueryService;

    public ControlPlaneAuditController(PlatformAuditQueryService platformAuditQueryService) {
        this.platformAuditQueryService = platformAuditQueryService;
    }

    @GetMapping
    public List<PlatformAuditQueryService.AuditLogRow> list(
            @RequestParam(defaultValue = "50") int limit) {
        return platformAuditQueryService.listRecent(limit);
    }
}

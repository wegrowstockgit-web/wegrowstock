package com.invsys.integration;

import com.invsys.api.dto.SyncLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class IntegrationSettingsController {

    private final IntegrationSettingsService integrationSettingsService;

    public IntegrationSettingsController(IntegrationSettingsService integrationSettingsService) {
        this.integrationSettingsService = integrationSettingsService;
    }

    @GetMapping("/sync-logs")
    public List<SyncLogResponse> listSyncLogs(
            @RequestParam(required = false) String system,
            @RequestParam(required = false) String status) {
        return integrationSettingsService.listSyncLogs(system, status);
    }

    @PostMapping("/sync-logs/{id}/retry")
    public SyncLogResponse retry(@PathVariable UUID id) {
        return integrationSettingsService.retry(id);
    }
}

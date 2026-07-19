package com.invsys.integration;

import com.invsys.api.dto.IntegrationChannelResponse;
import com.invsys.api.dto.IntegrationChannelUpsertRequest;
import com.invsys.api.dto.SyncLogResponse;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.service.IntegrationChannelService;
import com.invsys.service.IntegrationHubService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class IntegrationSettingsController {

    private final IntegrationSettingsService integrationSettingsService;
    private final IntegrationHubService integrationHubService;
    private final IntegrationChannelService integrationChannelService;

    public IntegrationSettingsController(IntegrationSettingsService integrationSettingsService,
                                         IntegrationHubService integrationHubService,
                                         IntegrationChannelService integrationChannelService) {
        this.integrationSettingsService = integrationSettingsService;
        this.integrationHubService = integrationHubService;
        this.integrationChannelService = integrationChannelService;
    }

    @GetMapping("/hub")
    public IntegrationHubService.HubStatus hub() {
        return integrationHubService.hubStatus();
    }

    /**
     * Hub vault channels ({@code integration_channels}) — distinct from legacy
     * {@code /integrations/channels} shop-link rows.
     */
    @GetMapping("/hub/channels")
    public List<IntegrationChannelResponse> listHubChannels() {
        return integrationChannelService.list();
    }

    @PutMapping("/hub/channels/{channelType}")
    public IntegrationChannelResponse upsertHubChannel(
            @PathVariable String channelType,
            @Valid @RequestBody IntegrationChannelUpsertRequest request) {
        IntegrationChannelUpsertRequest normalized = new IntegrationChannelUpsertRequest(
                channelType,
                request.status(),
                request.credentials(),
                request.settings());
        return integrationChannelService.upsert(normalized);
    }

    @DeleteMapping("/hub/channels/{channelType}")
    public IntegrationChannelResponse disconnectHubChannel(@PathVariable String channelType) {
        return integrationChannelService.disconnect(
                IntegrationChannelType.valueOf(channelType.trim().toUpperCase(Locale.ROOT)));
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

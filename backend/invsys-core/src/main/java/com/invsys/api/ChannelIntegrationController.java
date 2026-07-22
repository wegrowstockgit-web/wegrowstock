package com.invsys.api;

import com.invsys.api.dto.ChannelIntegrationResponse;
import com.invsys.domain.ChannelIntegration;
import com.invsys.service.ChannelIntegrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/channels")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ChannelIntegrationController {

    private final ChannelIntegrationService channelIntegrationService;

    public ChannelIntegrationController(ChannelIntegrationService channelIntegrationService) {
        this.channelIntegrationService = channelIntegrationService;
    }

    @GetMapping
    public List<ChannelIntegrationResponse> list() {
        return channelIntegrationService.list().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ChannelIntegrationResponse connect(@Valid @RequestBody ConnectRequest request) {
        return toResponse(channelIntegrationService.connect(request.platform(), request.shopIdentifier()));
    }

    @DeleteMapping("/{id}")
    public void disconnect(@PathVariable UUID id) {
        channelIntegrationService.disconnect(id);
    }

    private ChannelIntegrationResponse toResponse(ChannelIntegration integration) {
        return new ChannelIntegrationResponse(
                integration.getId(),
                integration.getPlatform(),
                integration.getShopIdentifier(),
                integration.getStatus());
    }

    public record ConnectRequest(
            @NotBlank String platform,
            @NotBlank String shopIdentifier
    ) {
    }
}

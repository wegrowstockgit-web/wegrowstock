package com.invsys.api;

import com.invsys.auth.SsoProviderCatalog;
import com.invsys.service.SsoConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings/sso")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class SsoConfigController {

    private final SsoConfigService ssoConfigService;
    private final SsoProviderCatalog ssoProviderCatalog;

    public SsoConfigController(SsoConfigService ssoConfigService, SsoProviderCatalog ssoProviderCatalog) {
        this.ssoConfigService = ssoConfigService;
        this.ssoProviderCatalog = ssoProviderCatalog;
    }

    @GetMapping
    public SsoConfigResponse get() {
        return ssoConfigService.getForCurrentTenant()
                .map(view -> SsoConfigResponse.from(view, ssoProviderCatalog.inferProvider(view.issuerUrl())))
                .orElse(SsoConfigResponse.empty());
    }

    @GetMapping("/connection-states")
    public Map<String, Object> connectionStates() {
        var current = ssoConfigService.getForCurrentTenant();
        String activeProvider = current.map(v -> ssoProviderCatalog.inferProvider(v.issuerUrl())).orElse("NONE");
        boolean enabled = current.map(SsoConfigService.SsoConfigView::enabled).orElse(false);
        boolean configured = current.map(SsoConfigService.SsoConfigView::hasSecret).orElse(false);
        List<Map<String, Object>> cards = ssoProviderCatalog.presets().stream()
                .map(p -> {
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("id", p.id());
                    card.put("displayName", p.displayName());
                    card.put("issuerTemplate", p.issuerTemplate());
                    boolean connected = enabled && configured && p.id().equals(activeProvider);
                    card.put("status", connected ? "CONNECTED" : "DISCONNECTED");
                    card.put("connected", connected);
                    return card;
                })
                .toList();
        return Map.of("providers", cards, "activeProvider", activeProvider);
    }

    @PutMapping
    public SsoConfigResponse upsert(@RequestBody UpsertSsoRequest request) {
        var view = ssoConfigService.upsert(new SsoConfigService.UpsertRequest(
                request.issuerUrl(),
                request.clientId(),
                request.clientSecret(),
                request.enabled(),
                request.forceSso(),
                request.protocol(),
                request.samlMetadataUrl(),
                request.samlEntityId()
        ));
        return SsoConfigResponse.from(view, ssoProviderCatalog.inferProvider(view.issuerUrl()));
    }

    public record UpsertSsoRequest(
            String issuerUrl,
            String clientId,
            String clientSecret,
            boolean enabled,
            boolean forceSso,
            String protocol,
            String samlMetadataUrl,
            String samlEntityId
    ) {
    }

    public record SsoConfigResponse(
            String issuerUrl,
            String clientId,
            boolean enabled,
            boolean forceSso,
            boolean configured,
            String protocol,
            String samlMetadataUrl,
            String samlEntityId,
            String provider
    ) {
        static SsoConfigResponse from(SsoConfigService.SsoConfigView view, String provider) {
            return new SsoConfigResponse(
                    view.issuerUrl(),
                    view.clientId(),
                    view.enabled(),
                    view.forceSso(),
                    view.hasSecret(),
                    view.protocol(),
                    view.samlMetadataUrl(),
                    view.samlEntityId(),
                    provider
            );
        }

        static SsoConfigResponse empty() {
            return new SsoConfigResponse("", "", false, false, false, "OIDC", null, null, "CUSTOM");
        }
    }
}

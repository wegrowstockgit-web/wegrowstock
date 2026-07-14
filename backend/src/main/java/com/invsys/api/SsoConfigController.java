package com.invsys.api;

import com.invsys.service.SsoConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/sso")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class SsoConfigController {

    private final SsoConfigService ssoConfigService;

    public SsoConfigController(SsoConfigService ssoConfigService) {
        this.ssoConfigService = ssoConfigService;
    }

    @GetMapping
    public SsoConfigResponse get() {
        return ssoConfigService.getForCurrentTenant()
                .map(SsoConfigResponse::from)
                .orElse(SsoConfigResponse.empty());
    }

    @PutMapping
    public SsoConfigResponse upsert(@RequestBody UpsertSsoRequest request) {
        return SsoConfigResponse.from(ssoConfigService.upsert(new SsoConfigService.UpsertRequest(
                request.issuerUrl(),
                request.clientId(),
                request.clientSecret(),
                request.enabled(),
                request.forceSso(),
                request.protocol(),
                request.samlMetadataUrl(),
                request.samlEntityId()
        )));
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
            String samlEntityId
    ) {
        static SsoConfigResponse from(SsoConfigService.SsoConfigView view) {
            return new SsoConfigResponse(
                    view.issuerUrl(),
                    view.clientId(),
                    view.enabled(),
                    view.forceSso(),
                    view.hasSecret(),
                    view.protocol(),
                    view.samlMetadataUrl(),
                    view.samlEntityId()
            );
        }

        static SsoConfigResponse empty() {
            return new SsoConfigResponse("", "", false, false, false, "OIDC", null, null);
        }
    }
}

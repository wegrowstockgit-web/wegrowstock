package com.invsys.core.security.saml;

import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/saml2")
public class SamlAuthController {

    private final BootstrapJdbc bootstrapJdbc;

    public SamlAuthController(BootstrapJdbc bootstrapJdbc) {
        this.bootstrapJdbc = bootstrapJdbc;
    }

    @GetMapping("/authenticate/{tenantId}")
    public ResponseEntity<?> authenticate(@PathVariable UUID tenantId) {
        var sso = bootstrapJdbc.findSsoConfigByTenantId(tenantId).orElse(null);
        if (sso == null || !sso.enabled()) {
            return ResponseEntity.status(302)
                    .location(URI.create("/oauth2/authorization/" + tenantId))
                    .build();
        }

        String protocol = sso.protocol() != null ? sso.protocol() : "OIDC";
        if ("SAML".equalsIgnoreCase(protocol)
                && sso.samlMetadataUrl() != null
                && !sso.samlMetadataUrl().isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("protocol", "SAML");
            body.put("metadataUrl", sso.samlMetadataUrl());
            body.put("entityId", sso.samlEntityId());
            body.put("tenantId", tenantId.toString());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }

        return ResponseEntity.status(302)
                .location(URI.create("/oauth2/authorization/" + tenantId))
                .build();
    }
}

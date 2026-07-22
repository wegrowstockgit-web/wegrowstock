package com.invsys.core.security;

import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves corporate email domains to the tenant's registered OIDC/SAML IdP entrypoint.
 */
@Service
public class TenantSsoResolver {

    private final BootstrapJdbc bootstrapJdbc;

    public TenantSsoResolver(BootstrapJdbc bootstrapJdbc) {
        this.bootstrapJdbc = bootstrapJdbc;
    }

    public Optional<SsoRoute> resolveByEmail(String email) {
        if (email == null || !email.contains("@")) {
            return Optional.empty();
        }
        String domain = email.substring(email.indexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
        if (domain.isBlank()) {
            return Optional.empty();
        }
        return bootstrapJdbc.findEnabledSsoByEmailDomain(domain).map(row -> {
            String protocol = row.protocol() != null ? row.protocol() : "OIDC";
            String authUrl = "SAML".equalsIgnoreCase(protocol)
                    ? "/saml2/authenticate/" + row.tenantId()
                    : "/oauth2/authorization/" + row.tenantId();
            return new SsoRoute(row.tenantId(), protocol, authUrl, row.issuerUrl(), row.forceSso());
        });
    }

    public Optional<SsoRoute> resolveByTenantId(UUID tenantId) {
        return bootstrapJdbc.findSsoConfigByTenantId(tenantId)
                .filter(BootstrapJdbc.SsoBootstrapRow::enabled)
                .map(row -> {
                    String protocol = row.protocol() != null ? row.protocol() : "OIDC";
                    String authUrl = "SAML".equalsIgnoreCase(protocol)
                            ? "/saml2/authenticate/" + tenantId
                            : "/oauth2/authorization/" + tenantId;
                    return new SsoRoute(tenantId, protocol, authUrl, row.issuerUrl(), row.forceSso());
                });
    }

    public record SsoRoute(UUID tenantId, String protocol, String authorizationUrl,
                           String issuerUrl, boolean forceSso) {
    }
}

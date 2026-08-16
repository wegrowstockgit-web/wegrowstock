package com.invsys.core.security;

import com.invsys.core.security.dto.HomeRealmDiscoveryResponse;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class HomeRealmDiscoveryService {

    private final BootstrapJdbc bootstrapJdbc;

    public HomeRealmDiscoveryService(BootstrapJdbc bootstrapJdbc) {
        this.bootstrapJdbc = bootstrapJdbc;
    }

    public HomeRealmDiscoveryResponse discover(String email, String clientIp) {
        Optional<BootstrapJdbc.HrdTenantRow> byIp = resolveByCorporateIp(clientIp);
        if (byIp.isPresent()) {
            return toResponse(byIp.get());
        }
        String domain = extractDomain(email);
        if (domain == null) {
            return HomeRealmDiscoveryResponse.passwordOnly();
        }
        return bootstrapJdbc.findHrdByVerifiedDomain(domain)
                .map(this::toResponse)
                .orElseGet(HomeRealmDiscoveryResponse::passwordOnly);
    }

    private Optional<BootstrapJdbc.HrdTenantRow> resolveByCorporateIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp)) {
            return Optional.empty();
        }
        for (BootstrapJdbc.HrdTenantRow row : bootstrapJdbc.listEnabledSsoWithCorporateCidrs()) {
            if (CorporateCidrMatcher.matches(clientIp, row.corporateCidrIps())) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    private HomeRealmDiscoveryResponse toResponse(BootstrapJdbc.HrdTenantRow row) {
        boolean ssoLive = row.ssoEnabled();
        String protocol = row.protocol() != null ? row.protocol() : "OIDC";
        String ssoUrl = null;
        String ssoType = null;
        if (ssoLive) {
            ssoType = "SAML".equalsIgnoreCase(protocol) ? "SAML" : "OIDC";
            ssoUrl = "SAML".equalsIgnoreCase(protocol)
                    ? "/saml2/authenticate/" + row.tenantId()
                    : "/oauth2/authorization/" + row.tenantId();
        }
        boolean passwordAllowed = !ssoLive || !row.forceSso();
        return new HomeRealmDiscoveryResponse(
                row.tenantId(),
                ssoType,
                ssoUrl,
                passwordAllowed,
                row.companyName());
    }

    static String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String domain = email.substring(email.indexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
        return domain.isBlank() ? null : domain;
    }

}

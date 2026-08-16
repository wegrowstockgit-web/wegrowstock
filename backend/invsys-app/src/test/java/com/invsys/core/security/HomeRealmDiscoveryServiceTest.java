package com.invsys.core.security;

import com.invsys.core.security.dto.HomeRealmDiscoveryResponse;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeRealmDiscoveryServiceTest {

    @Mock BootstrapJdbc bootstrapJdbc;
    @InjectMocks HomeRealmDiscoveryService service;

    @Test
    void prefersCorporateIpOverEmail() {
        UUID tenantId = UUID.randomUUID();
        when(bootstrapJdbc.listEnabledSsoWithCorporateCidrs()).thenReturn(List.of(
                new BootstrapJdbc.HrdTenantRow(
                        tenantId, "Warehouse Co", true, true, "OIDC", "OKTA",
                        List.of("203.0.113.0/24"))));

        HomeRealmDiscoveryResponse found = service.discover("user@other.com", "203.0.113.44");
        assertThat(found.tenantId()).isEqualTo(tenantId);
        assertThat(found.companyName()).isEqualTo("Warehouse Co");
        assertThat(found.isPasswordAllowed()).isFalse();
        assertThat(found.ssoUrl()).isEqualTo("/oauth2/authorization/" + tenantId);
        assertThat(found.ssoType()).isEqualTo("OIDC");
    }

    @Test
    void fallsBackToVerifiedEmailDomain() {
        UUID tenantId = UUID.randomUUID();
        when(bootstrapJdbc.listEnabledSsoWithCorporateCidrs()).thenReturn(List.of());
        when(bootstrapJdbc.findHrdByVerifiedDomain("acme.com")).thenReturn(Optional.of(
                new BootstrapJdbc.HrdTenantRow(
                        tenantId, "Acme Corp", true, false, "SAML", "CUSTOM_SAML", List.of())));

        HomeRealmDiscoveryResponse found = service.discover("buyer@acme.com", "198.51.100.9");
        assertThat(found.companyName()).isEqualTo("Acme Corp");
        assertThat(found.ssoType()).isEqualTo("SAML");
        assertThat(found.ssoUrl()).isEqualTo("/saml2/authenticate/" + tenantId);
        assertThat(found.isPasswordAllowed()).isTrue();
    }

    @Test
    void passwordOnlyWhenNothingMatches() {
        when(bootstrapJdbc.listEnabledSsoWithCorporateCidrs()).thenReturn(List.of());
        HomeRealmDiscoveryResponse none = service.discover("owner@demo.test", "127.0.0.1");
        assertThat(none.isPasswordAllowed()).isTrue();
        assertThat(none.ssoUrl()).isNull();
        assertThat(none.tenantId()).isNull();
        assertThat(HomeRealmDiscoveryService.extractDomain("bad")).isNull();
    }
}

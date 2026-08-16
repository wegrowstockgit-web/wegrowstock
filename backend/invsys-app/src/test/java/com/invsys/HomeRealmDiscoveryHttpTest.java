package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.TenantDomain;
import com.invsys.domain.TenantSsoConfig;
import com.invsys.repository.TenantDomainRepository;
import com.invsys.repository.TenantSsoConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class HomeRealmDiscoveryHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantDomainRepository tenantDomainRepository;
    @Autowired TenantSsoConfigRepository tenantSsoConfigRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void discovery_isPublicAndPasswordOnlyWithoutMatch() throws Exception {
        mockMvc.perform(get("/api/v1/auth/discovery").param("email", "owner@demo.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPasswordAllowed").value(true))
                .andExpect(jsonPath("$.ssoUrl").doesNotExist());
    }

    @Test
    void discovery_usesVerifiedDomainAndWmsSso() throws Exception {
        String slug = "hrd-" + UUID.randomUUID().toString().substring(0, 8);
        String domain = slug + ".example";
        TokenResponse owner = authService.signup(new SignupRequest(
                "Acme Corp", slug, "owner@" + domain, "password123", "Owner"));

        TenantContext.setTenantId(owner.tenantId());
        TenantDomain row = new TenantDomain();
        row.setTenantId(owner.tenantId());
        row.setDomainName(domain);
        row.setVerificationStatus("VERIFIED");
        row.setVerified(true);
        row.setDnsVerificationToken("growstock-verification=" + owner.tenantId());
        tenantDomainRepository.save(row);

        TenantSsoConfig sso = new TenantSsoConfig();
        sso.setTenantId(owner.tenantId());
        sso.setIssuerUrl("https://idp.example/oauth");
        sso.setClientId("acme");
        sso.setEncryptedClientSecret(new byte[] {1, 2, 3, 4});
        sso.setEnabled(true);
        sso.setForceSso(true);
        sso.setProtocol("OIDC");
        sso.setSsoProvider("OKTA");
        tenantSsoConfigRepository.save(sso);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/auth/discovery").param("email", "buyer@" + domain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(owner.tenantId().toString()))
                .andExpect(jsonPath("$.companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.ssoType").value("OIDC"))
                .andExpect(jsonPath("$.ssoUrl").value("/oauth2/authorization/" + owner.tenantId()))
                .andExpect(jsonPath("$.isPasswordAllowed").value(false));
    }

    @Test
    void discovery_matchesCorporateCidrBeforeEmail() throws Exception {
        String slug = "cidr-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Warehouse Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(put("/api/v1/settings/sso")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issuerUrl":"https://login.microsoftonline.com/contoso/v2.0",
                                  "clientId":"entra-client",
                                  "clientSecret":"super-secret",
                                  "enabled":true,
                                  "forceSso":false,
                                  "enforceSso":false,
                                  "protocol":"OIDC",
                                  "ssoProvider":"ENTRA",
                                  "corporateCidrIps":["203.0.113.0/24"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateCidrIps[0]").value("203.0.113.0/24"));

        mockMvc.perform(get("/api/v1/auth/discovery")
                        .param("email", "visitor@other.test")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.88");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Warehouse Co"))
                .andExpect(jsonPath("$.ssoType").value("OIDC"))
                .andExpect(jsonPath("$.isPasswordAllowed").value(true))
                .andExpect(jsonPath("$.ssoUrl").value("/oauth2/authorization/" + owner.tenantId()));
    }
}

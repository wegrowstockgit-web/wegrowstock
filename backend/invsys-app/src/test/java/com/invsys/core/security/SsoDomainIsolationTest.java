package com.invsys.core.security;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.common.ApiException;
import com.invsys.domain.TenantDomain;
import com.invsys.domain.TenantSsoConfig;
import com.invsys.repository.TenantDomainRepository;
import com.invsys.repository.TenantSsoConfigRepository;
import com.invsys.service.TenantDomainService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SsoDomainIsolationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantDomainRepository tenantDomainRepository;
    @Autowired TenantSsoConfigRepository tenantSsoConfigRepository;
    @Autowired TenantDomainService tenantDomainService;
    @Autowired TestDataHelper testDataHelper;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void attackerForceSsoDomainDoesNotBlockVictimTenantPasswordLogin() {
        String slugA = "vic-" + UUID.randomUUID().toString().substring(0, 8);
        String slugB = "atk-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse victim = authService.signup(new SignupRequest(
                "Victim Co", slugA, "user@" + slugA + ".com", "password123", "Victim"));

        TokenResponse attacker = authService.signup(new SignupRequest(
                "Attacker Co", slugB, "admin@" + slugB + ".com", "password123", "Attacker"));

        TenantContext.setTenantId(attacker.tenantId());
        TenantDomain stolen = new TenantDomain();
        stolen.setTenantId(attacker.tenantId());
        stolen.setDomainName(slugA + ".com");
        stolen.setVerificationStatus("VERIFIED");
        tenantDomainRepository.save(stolen);

        TenantSsoConfig sso = tenantSsoConfigRepository.findByTenantId(attacker.tenantId())
                .orElseGet(TenantSsoConfig::new);
        sso.setTenantId(attacker.tenantId());
        sso.setIssuerUrl("https://attacker.example/oauth");
        sso.setClientId("evil");
        sso.setEncryptedClientSecret(new byte[] {1, 2, 3, 4});
        sso.setEnabled(true);
        sso.setForceSso(true);
        sso.setProtocol("OIDC");
        tenantSsoConfigRepository.save(sso);
        TenantContext.clear();

        TokenResponse login = authService.login(new LoginRequest("user@" + slugA + ".com", "password123"));
        assertThat(login.tenantId()).isEqualTo(victim.tenantId());
        assertThat(login.userId()).isEqualTo(victim.userId());
    }

    @Test
    void ssoDiscoverOmitsTenantAndIssuerMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sso-discover").param("email", "nobody@example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ssoRequired").value(false))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.issuerUrl").doesNotExist());
    }

    @Test
    void cannotVerifyDomainAlreadyVerifiedByAnotherTenant() {
        String domain = "shared-" + UUID.randomUUID().toString().substring(0, 8) + ".com";
        UUID tenantA = testDataHelper.createTenant("A Co", "a-" + UUID.randomUUID().toString().substring(0, 6));
        UUID tenantB = testDataHelper.createTenant("B Co", "b-" + UUID.randomUUID().toString().substring(0, 6));

        TenantContext.setTenantId(tenantA);
        TenantDomain owned = new TenantDomain();
        owned.setTenantId(tenantA);
        owned.setDomainName(domain);
        owned.setVerificationStatus("VERIFIED");
        tenantDomainRepository.save(owned);
        TenantContext.clear();

        TenantContext.setTenantId(tenantB);
        TenantDomain pending = new TenantDomain();
        pending.setTenantId(tenantB);
        pending.setDomainName(domain);
        pending.setVerificationStatus("PENDING");
        pending = tenantDomainRepository.save(pending);

        UUID pendingId = pending.getId();
        assertThatThrownBy(() -> tenantDomainService.verify(pendingId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("DOMAIN_IN_USE"));
        TenantContext.clear();
    }
}

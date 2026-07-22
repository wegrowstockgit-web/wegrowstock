package com.invsys;

import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.common.ApiException;
import com.invsys.domain.TenantSsoConfig;
import com.invsys.domain.User;
import com.invsys.core.integration.CredentialVaultService;
import com.invsys.repository.TenantSsoConfigRepository;
import com.invsys.repository.UserRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.invsys.core.security.AuthService;

@SpringBootTest
@ActiveProfiles("test")
class SsoLoginGateTest extends AbstractIntegrationTest {

    @Autowired com.invsys.core.security.AuthService authService;
    @Autowired TestDataHelper testDataHelper;
    @Autowired TenantSsoConfigRepository ssoConfigRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CredentialVaultService credentialVaultService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void forcedSsoRejectsPasswordLoginWithRedirect() {
        String slug = "sso-corp-" + UUID.randomUUID().toString().substring(0, 8);
        final UUID tenantId = testDataHelper.createTenant("SSO Corp", slug);
        TenantContext.setTenantId(tenantId);

        TenantContext.setTenantId(tenantId);

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail("owner@sso.test");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setDisplayName("SSO Owner");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        TenantSsoConfig config = new TenantSsoConfig();
        config.setTenantId(tenantId);
        config.setIssuerUrl("https://issuer.example.com");
        config.setClientId("client-id");
        config.setEncryptedClientSecret(credentialVaultService.encrypt("secret".getBytes(StandardCharsets.UTF_8)));
        config.setEnabled(true);
        config.setForceSso(true);
        ssoConfigRepository.save(config);

        assertThatThrownBy(() -> authService.login(new LoginRequest("owner@sso.test", "password123")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(api.getCode()).isEqualTo("SSO_REQUIRED");
                    assertThat(api.getProperties().get("ssoAuthorizationUrl"))
                            .isEqualTo("/oauth2/authorization/" + tenantId);
                });
    }
}

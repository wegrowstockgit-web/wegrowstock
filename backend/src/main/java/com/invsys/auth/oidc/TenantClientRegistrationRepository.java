package com.invsys.auth.oidc;

import com.invsys.integration.CredentialVaultService;
import com.invsys.tenancy.BootstrapJdbc;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.UUID;

@Component
public class TenantClientRegistrationRepository implements ClientRegistrationRepository {

    private final BootstrapJdbc bootstrapJdbc;
    private final CredentialVaultService credentialVaultService;

    public TenantClientRegistrationRepository(BootstrapJdbc bootstrapJdbc,
                                              CredentialVaultService credentialVaultService) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.credentialVaultService = credentialVaultService;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        UUID tenantId;
        try {
            tenantId = UUID.fromString(registrationId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return bootstrapJdbc.findSsoConfigByTenantId(tenantId)
                .filter(BootstrapJdbc.SsoBootstrapRow::enabled)
                .map(row -> toRegistration(tenantId, row))
                .orElse(null);
    }

    private ClientRegistration toRegistration(UUID tenantId, BootstrapJdbc.SsoBootstrapRow row) {
        String secret = new String(credentialVaultService.decrypt(row.encryptedClientSecret()),
                StandardCharsets.UTF_8);
        return ClientRegistration.withRegistrationId(tenantId.toString())
                .clientId(row.clientId())
                .clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(row.issuerUrl())
                .build();
    }
}

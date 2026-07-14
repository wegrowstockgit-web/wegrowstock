package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.TenantSsoConfig;
import com.invsys.integration.CredentialVaultService;
import com.invsys.repository.TenantSsoConfigRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
public class SsoConfigService {

    private final TenantSsoConfigRepository repository;
    private final CredentialVaultService credentialVaultService;

    public SsoConfigService(TenantSsoConfigRepository repository,
                            CredentialVaultService credentialVaultService) {
        this.repository = repository;
        this.credentialVaultService = credentialVaultService;
    }

    public Optional<SsoConfigView> getForCurrentTenant() {
        return repository.findByTenantId(TenantContext.requireTenantId()).map(this::toView);
    }

    @Transactional
    public SsoConfigView upsert(UpsertRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSsoConfig config = repository.findByTenantId(tenantId).orElseGet(() -> {
            TenantSsoConfig created = new TenantSsoConfig();
            created.setTenantId(tenantId);
            return created;
        });
        config.setIssuerUrl(request.issuerUrl().trim());
        config.setClientId(request.clientId().trim());
        if (request.clientSecret() != null && !request.clientSecret().isBlank()) {
            config.setEncryptedClientSecret(
                    credentialVaultService.encrypt(request.clientSecret().getBytes(StandardCharsets.UTF_8)));
        } else if (config.getEncryptedClientSecret() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Client secret is required");
        }
        config.setEnabled(request.enabled());
        config.setForceSso(request.forceSso());
        return toView(repository.save(config));
    }

    public Optional<ResolvedSsoConfig> resolve(UUID tenantId) {
        return repository.findByTenantId(tenantId)
                .filter(TenantSsoConfig::isEnabled)
                .map(config -> new ResolvedSsoConfig(
                        tenantId,
                        config.getIssuerUrl(),
                        config.getClientId(),
                        new String(credentialVaultService.decrypt(config.getEncryptedClientSecret()),
                                StandardCharsets.UTF_8),
                        config.isForceSso()
                ));
    }

    private SsoConfigView toView(TenantSsoConfig config) {
        return new SsoConfigView(
                config.getIssuerUrl(),
                config.getClientId(),
                config.isEnabled(),
                config.isForceSso(),
                config.getEncryptedClientSecret() != null
        );
    }

    public record UpsertRequest(String issuerUrl, String clientId, String clientSecret,
                              boolean enabled, boolean forceSso) {
    }

    public record SsoConfigView(String issuerUrl, String clientId, boolean enabled,
                                boolean forceSso, boolean hasSecret) {
    }

    public record ResolvedSsoConfig(UUID tenantId, String issuerUrl, String clientId,
                                   String clientSecret, boolean forceSso) {
    }
}

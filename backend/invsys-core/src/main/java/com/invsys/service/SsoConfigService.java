package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.TenantSsoConfig;
import com.invsys.core.integration.CredentialVaultService;
import com.invsys.repository.TenantSsoConfigRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invsys.core.security.CorporateCidrMatcher;
import com.invsys.core.security.SsoProviderCatalog;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SsoConfigService {

    private final TenantSsoConfigRepository repository;
    private final CredentialVaultService credentialVaultService;
    private final SsoProviderCatalog ssoProviderCatalog;

    public SsoConfigService(TenantSsoConfigRepository repository,
                            CredentialVaultService credentialVaultService,
                            SsoProviderCatalog ssoProviderCatalog) {
        this.repository = repository;
        this.credentialVaultService = credentialVaultService;
        this.ssoProviderCatalog = ssoProviderCatalog;
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
        String protocol = request.protocol() != null && !request.protocol().isBlank()
                ? request.protocol().trim().toUpperCase()
                : "OIDC";
        config.setProtocol(protocol);
        config.setSamlMetadataUrl(blankToNull(request.samlMetadataUrl()));
        config.setSamlEntityId(blankToNull(request.samlEntityId()));

        if (request.issuerUrl() != null && !request.issuerUrl().isBlank()) {
            config.setIssuerUrl(request.issuerUrl().trim());
        } else if (config.getIssuerUrl() == null) {
            config.setIssuerUrl("SAML".equals(protocol) ? "saml://placeholder" : "");
        }
        if (request.clientId() != null && !request.clientId().isBlank()) {
            config.setClientId(request.clientId().trim());
        } else if (config.getClientId() == null) {
            config.setClientId("SAML".equals(protocol) ? "saml-placeholder" : "");
        }
        if (request.clientSecret() != null && !request.clientSecret().isBlank()) {
            config.setEncryptedClientSecret(
                    credentialVaultService.encrypt(request.clientSecret().getBytes(StandardCharsets.UTF_8)));
        } else if (config.getEncryptedClientSecret() == null) {
            if ("SAML".equals(protocol)) {
                config.setEncryptedClientSecret(
                        credentialVaultService.encrypt("saml-placeholder".getBytes(StandardCharsets.UTF_8)));
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Client secret is required");
            }
        }
        config.setEnabled(request.enabled());
        config.setForceSso(request.forceSso());
        config.setAcsUrl(blankToNull(request.acsUrl()));
        config.setSamlCertificate(blankToNull(request.samlCertificate()));
        config.setCorporateCidrIps(CorporateCidrMatcher.normalizeOrReject(request.corporateCidrIps()));
        String provider = request.ssoProvider();
        if (provider == null || provider.isBlank()) {
            provider = "SAML".equals(protocol)
                    ? "CUSTOM_SAML"
                    : ssoProviderCatalog.inferProvider(config.getIssuerUrl());
        }
        config.setSsoProvider(normalizeProvider(provider));
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
                        config.isForceSso(),
                        config.getProtocol() != null ? config.getProtocol() : "OIDC",
                        config.getSamlMetadataUrl(),
                        config.getSamlEntityId()
                ));
    }

    private SsoConfigView toView(TenantSsoConfig config) {
        return new SsoConfigView(
                config.getIssuerUrl(),
                config.getClientId(),
                config.isEnabled(),
                config.isForceSso(),
                config.getEncryptedClientSecret() != null,
                config.getProtocol() != null ? config.getProtocol() : "OIDC",
                config.getSamlMetadataUrl(),
                config.getSamlEntityId(),
                config.getSsoProvider() != null ? config.getSsoProvider() : "CUSTOM",
                config.getAcsUrl(),
                config.getSamlCertificate(),
                config.getCorporateCidrIps() != null ? config.getCorporateCidrIps() : List.of()
        );
    }

    private static String normalizeProvider(String raw) {
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "OKTA", "ENTRA", "GOOGLE", "CUSTOM_SAML", "CUSTOM" -> value;
            default -> "CUSTOM";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UpsertRequest(String issuerUrl, String clientId, String clientSecret,
                              boolean enabled, boolean forceSso,
                              String protocol, String samlMetadataUrl, String samlEntityId,
                              String ssoProvider, String acsUrl, String samlCertificate,
                              List<String> corporateCidrIps) {
        public UpsertRequest(String issuerUrl, String clientId, String clientSecret,
                             boolean enabled, boolean forceSso) {
            this(issuerUrl, clientId, clientSecret, enabled, forceSso, "OIDC", null, null,
                    null, null, null, List.of());
        }

        public UpsertRequest(String issuerUrl, String clientId, String clientSecret,
                             boolean enabled, boolean forceSso,
                             String protocol, String samlMetadataUrl, String samlEntityId) {
            this(issuerUrl, clientId, clientSecret, enabled, forceSso, protocol, samlMetadataUrl, samlEntityId,
                    null, null, null, List.of());
        }
    }

    public record SsoConfigView(String issuerUrl, String clientId, boolean enabled,
                                boolean forceSso, boolean hasSecret,
                                String protocol, String samlMetadataUrl, String samlEntityId,
                                String ssoProvider, String acsUrl, String samlCertificate,
                                List<String> corporateCidrIps) {
    }

    public record ResolvedSsoConfig(UUID tenantId, String issuerUrl, String clientId,
                                   String clientSecret, boolean forceSso,
                                   String protocol, String samlMetadataUrl, String samlEntityId) {
    }
}

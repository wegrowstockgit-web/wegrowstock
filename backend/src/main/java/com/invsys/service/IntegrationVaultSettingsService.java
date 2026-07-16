package com.invsys.service;

import com.invsys.integration.CredentialVaultService;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Encrypted credential vault for Settings integrations (accounting, Shopify, carriers).
 */
@Service
public class IntegrationVaultSettingsService {

    private static final Set<String> SYSTEMS = Set.of(
            "QUICKBOOKS", "XERO", "SHOPIFY", "EASYPOST", "UPS", "FEDEX"
    );

    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService vaultService;

    public IntegrationVaultSettingsService(IntegrationCredentialRepository credentialRepository,
                                           CredentialVaultService vaultService) {
        this.credentialRepository = credentialRepository;
        this.vaultService = vaultService;
    }

    public List<CredentialStatus> list(List<String> systems) {
        UUID tenantId = TenantContext.requireTenantId();
        List<String> wanted = systems == null || systems.isEmpty()
                ? SYSTEMS.stream().sorted().toList()
                : systems.stream().map(s -> s.toUpperCase(Locale.ROOT)).filter(SYSTEMS::contains).toList();
        return wanted.stream()
                .map(system -> credentialRepository.findByTenantIdAndSystem(tenantId, system)
                        .filter(c -> "CONNECTED".equalsIgnoreCase(c.getStatus()))
                        .map(c -> new CredentialStatus(system, "CONNECTED", true))
                        .orElse(new CredentialStatus(system, "NOT_CONFIGURED", false)))
                .toList();
    }

    @Transactional
    public CredentialStatus save(String system, String apiKey) {
        String normalized = validateSystem(system);
        UUID tenantId = TenantContext.requireTenantId();
        IntegrationCredential credential = credentialRepository.findByTenantIdAndSystem(tenantId, normalized)
                .orElseGet(() -> {
                    IntegrationCredential created = new IntegrationCredential();
                    created.setTenantId(tenantId);
                    created.setSystem(normalized);
                    return created;
                });
        credential.setCiphertext(vaultService.encrypt(apiKey.getBytes(StandardCharsets.UTF_8)));
        credential.setStatus("CONNECTED");
        credentialRepository.save(credential);
        return new CredentialStatus(normalized, "CONNECTED", true);
    }

    @Transactional
    public CredentialStatus disconnect(String system) {
        String normalized = validateSystem(system);
        UUID tenantId = TenantContext.requireTenantId();
        credentialRepository.findByTenantIdAndSystem(tenantId, normalized).ifPresent(credentialRepository::delete);
        return new CredentialStatus(normalized, "NOT_CONFIGURED", false);
    }

    private String validateSystem(String system) {
        if (system == null || system.isBlank()) {
            throw new IllegalArgumentException("system is required");
        }
        String normalized = system.trim().toUpperCase(Locale.ROOT);
        if (!SYSTEMS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported integration system: " + system);
        }
        return normalized;
    }

    public record CredentialStatus(String system, String status, boolean connected) {
    }
}

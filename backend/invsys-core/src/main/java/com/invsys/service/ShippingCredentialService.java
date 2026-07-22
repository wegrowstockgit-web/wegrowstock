package com.invsys.service;

import com.invsys.core.integration.CredentialVaultService;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class ShippingCredentialService {

    private static final List<String> SHIPPING_SYSTEMS = List.of("EASYPOST", "UPS", "FEDEX");

    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService vaultService;

    public ShippingCredentialService(IntegrationCredentialRepository credentialRepository,
                                     CredentialVaultService vaultService) {
        this.credentialRepository = credentialRepository;
        this.vaultService = vaultService;
    }

    public List<ShippingCredentialStatus> list() {
        UUID tenantId = TenantContext.requireTenantId();
        return SHIPPING_SYSTEMS.stream()
                .map(system -> {
                    boolean connected = credentialRepository.findByTenantIdAndSystem(tenantId, system).isPresent();
                    return new ShippingCredentialStatus(system, connected ? "CONNECTED" : "NOT_CONFIGURED");
                })
                .toList();
    }

    @Transactional
    public ShippingCredentialStatus save(String system, String apiKey) {
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
        return new ShippingCredentialStatus(normalized, "CONNECTED");
    }

    @Transactional
    public ShippingCredentialStatus disconnect(String system) {
        String normalized = validateSystem(system);
        UUID tenantId = TenantContext.requireTenantId();
        credentialRepository.findByTenantIdAndSystem(tenantId, normalized).ifPresent(credentialRepository::delete);
        return new ShippingCredentialStatus(normalized, "NOT_CONFIGURED");
    }

    /**
     * Decrypts the tenant's vaulted carrier / EasyPost API key for outbox label purchase.
     * Prefer explicit carrier ({@code UPS}/{@code FEDEX}) when present; otherwise {@code EASYPOST}.
     */
    public String resolveApiKey(String preferredSystem) {
        UUID tenantId = TenantContext.requireTenantId();
        String preferred = preferredSystem == null ? "EASYPOST" : preferredSystem.trim().toUpperCase();
        return decryptIfPresent(tenantId, preferred)
                .or(() -> decryptIfPresent(tenantId, "EASYPOST"))
                .orElseThrow(() -> new IllegalStateException(
                        "No shipping credentials configured for " + preferred + " (or EASYPOST fallback)"));
    }

    private java.util.Optional<String> decryptIfPresent(UUID tenantId, String system) {
        return credentialRepository.findByTenantIdAndSystem(tenantId, system)
                .filter(c -> "CONNECTED".equalsIgnoreCase(c.getStatus()))
                .map(c -> new String(vaultService.decrypt(c.getCiphertext()), StandardCharsets.UTF_8));
    }

    private String validateSystem(String system) {
        String normalized = system == null ? "" : system.trim().toUpperCase();
        if (!SHIPPING_SYSTEMS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported shipping system: " + system);
        }
        return normalized;
    }

    public record ShippingCredentialStatus(String system, String status) {
    }
}

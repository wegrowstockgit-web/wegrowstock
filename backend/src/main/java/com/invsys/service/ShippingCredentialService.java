package com.invsys.service;

import com.invsys.integration.CredentialVaultService;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.tenancy.TenantContext;
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
        validateSystem(system);
        UUID tenantId = TenantContext.requireTenantId();
        IntegrationCredential credential = credentialRepository.findByTenantIdAndSystem(tenantId, system)
                .orElseGet(() -> {
                    IntegrationCredential created = new IntegrationCredential();
                    created.setTenantId(tenantId);
                    created.setSystem(system);
                    return created;
                });
        credential.setCiphertext(vaultService.encrypt(apiKey.getBytes(StandardCharsets.UTF_8)));
        credential.setStatus("CONNECTED");
        credentialRepository.save(credential);
        return new ShippingCredentialStatus(system, "CONNECTED");
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

    private void validateSystem(String system) {
        if (!SHIPPING_SYSTEMS.contains(system.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported shipping system: " + system);
        }
    }

    public record ShippingCredentialStatus(String system, String status) {
    }
}

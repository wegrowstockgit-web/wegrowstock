package com.invsys.service;

import com.invsys.api.dto.IntegrationChannelResponse;
import com.invsys.api.dto.IntegrationChannelUpsertRequest;
import com.invsys.common.ApiException;
import com.invsys.domain.IntegrationChannel;
import com.invsys.integration.CredentialVaultService;
import com.invsys.integration.channel.IntegrationChannelStatus;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.repository.IntegrationChannelRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Save/load lifecycle for {@link IntegrationChannel}: encrypts credential bags with
 * {@link CredentialVaultService} before persist and decrypts on internal load.
 */
@Service
public class IntegrationChannelService {

    private final IntegrationChannelRepository channelRepository;
    private final CredentialVaultService vaultService;
    private final ObjectMapper objectMapper;

    public IntegrationChannelService(IntegrationChannelRepository channelRepository,
                                     CredentialVaultService vaultService,
                                     ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.vaultService = vaultService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<IntegrationChannelResponse> list() {
        UUID tenantId = TenantContext.requireTenantId();
        return channelRepository.findByTenantIdOrderByChannelTypeAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<IntegrationChannel> findActive(IntegrationChannelType channelType) {
        return channelRepository.findActiveByTenantAndType(TenantContext.requireTenantId(), channelType);
    }

    /**
     * Decrypts vaulted credentials for an active channel (webhook ingestion / adapters).
     */
    @Transactional(readOnly = true)
    public Map<String, String> loadCredentials(IntegrationChannelType channelType) {
        IntegrationChannel channel = findActive(channelType)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHANNEL_NOT_CONNECTED",
                        "No active " + channelType + " integration channel"));
        return decryptCredentials(channel);
    }

    @Transactional(readOnly = true)
    public Map<String, String> decryptCredentials(IntegrationChannel channel) {
        if (channel == null || !channel.hasEncryptedCredentials()) {
            return Map.of();
        }
        try {
            byte[] plain = vaultService.decrypt(channel.getEncryptedCredentials());
            Map<String, String> secrets = objectMapper.readValue(plain, new TypeReference<>() {
            });
            channel.setCredentialSecrets(secrets);
            return secrets;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VAULT_DECRYPT_FAILED",
                    "Failed to decrypt integration credentials")
                    .withProperty("channelType", channel.getChannelType().name());
        }
    }

    @Transactional
    public IntegrationChannelResponse upsert(IntegrationChannelUpsertRequest request) {
        IntegrationChannelType type = parseChannelType(request.channelType());
        IntegrationChannelStatus status = parseStatus(request.status());
        UUID tenantId = TenantContext.requireTenantId();

        IntegrationChannel channel = channelRepository.findByTenantIdAndChannelType(tenantId, type)
                .orElseGet(() -> {
                    IntegrationChannel created = new IntegrationChannel();
                    created.setTenantId(tenantId);
                    created.setChannelType(type);
                    return created;
                });

        channel.setStatus(status);
        if (request.settings() != null) {
            channel.setSettings(new LinkedHashMap<>(request.settings()));
        }
        if (request.credentials() != null && !request.credentials().isEmpty()) {
            encryptAndAttach(channel, request.credentials());
        } else if (status == IntegrationChannelStatus.DISCONNECTED) {
            channel.setEncryptedCredentials(null);
            channel.setCredentialSecrets(null);
        }

        return toResponse(channelRepository.save(channel));
    }

    @Transactional
    public IntegrationChannelResponse disconnect(IntegrationChannelType channelType) {
        UUID tenantId = TenantContext.requireTenantId();
        IntegrationChannel channel = channelRepository.findByTenantIdAndChannelType(tenantId, channelType)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Integration channel not found"));
        channel.setStatus(IntegrationChannelStatus.DISCONNECTED);
        channel.setEncryptedCredentials(null);
        channel.setCredentialSecrets(null);
        return toResponse(channelRepository.save(channel));
    }

    private void encryptAndAttach(IntegrationChannel channel, Map<String, String> credentials) {
        try {
            Map<String, String> sanitized = new LinkedHashMap<>();
            credentials.forEach((k, v) -> {
                if (k != null && v != null && !k.isBlank()) {
                    sanitized.put(k.trim(), v);
                }
            });
            byte[] json = objectMapper.writeValueAsBytes(sanitized);
            channel.setEncryptedCredentials(vaultService.encrypt(json));
            channel.setCredentialSecrets(Map.copyOf(sanitized));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VAULT_ENCRYPT_FAILED",
                    "Failed to encrypt integration credentials")
                    .withProperty("cause", ex.getMessage());
        }
    }

    private IntegrationChannelResponse toResponse(IntegrationChannel channel) {
        return new IntegrationChannelResponse(
                channel.getId(),
                channel.getChannelType().name(),
                channel.getStatus().name(),
                channel.hasEncryptedCredentials(),
                channel.getSettings() != null ? channel.getSettings() : Map.of(),
                channel.getCreatedAt(),
                channel.getUpdatedAt());
    }

    private static IntegrationChannelType parseChannelType(String raw) {
        try {
            return IntegrationChannelType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "channelType must be SHOPIFY, AMAZON, or EDI");
        }
    }

    private static IntegrationChannelStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return IntegrationChannelStatus.ACTIVE;
        }
        try {
            return IntegrationChannelStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "status must be ACTIVE, DISCONNECTED, or ERROR");
        }
    }
}

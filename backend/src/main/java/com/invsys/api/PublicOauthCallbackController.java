package com.invsys.api;

import com.invsys.common.ApiException;
import com.invsys.integration.CredentialVaultService;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.tenancy.BootstrapJdbc;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic public OAuth2 redirect listener for plug-and-play provider onboarding.
 * Path is permitAll + JwtAuthFilter shouldNotFilter via /api/v1/public/**.
 */
@RestController
@RequestMapping("/api/v1/public/oauth")
public class PublicOauthCallbackController {

    private final BootstrapJdbc bootstrapJdbc;
    private final CredentialVaultService credentialVaultService;
    private final IntegrationCredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    public PublicOauthCallbackController(BootstrapJdbc bootstrapJdbc,
                                         CredentialVaultService credentialVaultService,
                                         IntegrationCredentialRepository credentialRepository,
                                         ObjectMapper objectMapper) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.credentialVaultService = credentialVaultService;
        this.credentialRepository = credentialRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/callback/{provider}")
    public ResponseEntity<Map<String, Object>> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription) {

        if (error != null && !error.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OAUTH_ERROR",
                    errorDescription != null ? errorDescription : error);
        }
        if (state == null || state.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "state is required");
        }
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "code is required");
        }

        BootstrapJdbc.OauthStateRow stateRow = bootstrapJdbc.consumeOauthCallbackState(state)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE",
                        "Unknown or expired OAuth state"));
        if (stateRow.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "OAuth state expired");
        }
        if (!stateRow.provider().equalsIgnoreCase(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_MISMATCH",
                    "State provider does not match callback path");
        }

        Map<String, Object> vaultPayload = new LinkedHashMap<>();
        vaultPayload.put("provider", provider.toUpperCase());
        vaultPayload.put("code", code);
        vaultPayload.put("state", state);
        vaultPayload.put("capturedAt", Instant.now().toString());
        if (stateRow.payloadJson() != null && !stateRow.payloadJson().isBlank()) {
            vaultPayload.put("statePayload", stateRow.payloadJson());
        }

        UUID tenantId = stateRow.tenantId();
        TenantContext.setTenantId(tenantId);
        try {
            String system = "OAUTH_" + provider.toUpperCase();
            byte[] ciphertext = credentialVaultService.encrypt(
                    objectMapper.writeValueAsString(vaultPayload).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            IntegrationCredential credential = credentialRepository
                    .findByTenantIdAndSystem(tenantId, system)
                    .orElseGet(IntegrationCredential::new);
            credential.setTenantId(tenantId);
            credential.setSystem(system);
            credential.setCiphertext(ciphertext);
            credential.setKeyVersion(1);
            credential.setStatus("CONNECTED");
            credentialRepository.save(credential);

            // Do not echo tenantId to unauthenticated callers.
            return ResponseEntity.ok(Map.of(
                    "status", "CONNECTED",
                    "provider", provider.toUpperCase(),
                    "system", system));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "OAUTH_VAULT_FAILED",
                    "Failed to store OAuth credentials");
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Bootstrap helper: mint a short-lived state token bound to a tenant + provider.
     * Intended for authenticated settings flows that kick off external OAuth.
     */
    public String mintState(UUID tenantId, String provider, Map<String, Object> payload) {
        String state = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        try {
            String json = objectMapper.writeValueAsString(payload != null ? payload : Map.of());
            bootstrapJdbc.insertOauthCallbackState(
                    state, tenantId, provider.toLowerCase(), json, Instant.now().plusSeconds(600));
            return state;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to mint OAuth state", ex);
        }
    }
}

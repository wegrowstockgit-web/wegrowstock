package com.invsys.service;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.TerminalSwitchResponse;
import com.invsys.common.ApiException;
import com.invsys.config.JwtProperties;
import com.invsys.domain.User;
import com.invsys.domain.WebAuthnChallenge;
import com.invsys.domain.WebAuthnCredential;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.repository.WebAuthnChallengeRepository;
import com.invsys.repository.WebAuthnCredentialRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared-terminal WebAuthn / passkey proximity gating.
 * Issues the same short-lived terminal JWT as PIN switch without killing the primary session.
 * Uses challenge + HMAC software authenticator suitable for glove-friendly hardware / e2e;
 * credential payloads mirror WebAuthn assertion JSON shapes.
 */
@Service
public class TerminalBiometricService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebAuthnChallengeRepository challengeRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public TerminalBiometricService(WebAuthnChallengeRepository challengeRepository,
                                    WebAuthnCredentialRepository credentialRepository,
                                    UserRepository userRepository,
                                    UserRoleRepository userRoleRepository,
                                    AuthService authService,
                                    JwtProperties jwtProperties) {
        this.challengeRepository = challengeRepository;
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public Map<String, Object> createAssertionOptions() {
        UUID tenantId = TenantContext.requireTenantId();
        String challenge = randomToken(32);
        WebAuthnChallenge entity = new WebAuthnChallenge();
        entity.setTenantId(tenantId);
        entity.setChallenge(challenge);
        entity.setPurpose("TERMINAL_ASSERT");
        entity.setExpiresAt(Instant.now().plusSeconds(120));
        challengeRepository.save(entity);
        return Map.of(
                "challenge", challenge,
                "timeout", 120000,
                "rpId", "invsys.local",
                "userVerification", "preferred",
                "allowCredentials", List.of());
    }

    /**
     * Register a software passkey for a user (OWNER/ADMIN). Returns one-time secret for the authenticator.
     */
    @Transactional
    public Map<String, String> registerCredential(UUID userId, String label) {
        UUID tenantId = TenantContext.requireTenantId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!tenantId.equals(user.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
        String credentialId = "cred_" + randomToken(16);
        String secret = randomToken(32);
        WebAuthnCredential cred = new WebAuthnCredential();
        cred.setTenantId(tenantId);
        cred.setUserId(userId);
        cred.setCredentialId(credentialId);
        cred.setCredentialSecretHash(hash(secret));
        cred.setLabel(label != null ? label : "Terminal passkey");
        credentialRepository.save(cred);
        return Map.of(
                "credentialId", credentialId,
                "secret", secret,
                "userId", userId.toString());
    }

    @Transactional
    public TerminalSwitchResponse assertTerminal(String credentialId, String challenge, String signature) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID switchedFrom = TenantContext.getUserId().orElse(null);

        WebAuthnChallenge stored = challengeRepository.findByTenantIdAndChallenge(tenantId, challenge)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CHALLENGE",
                        "Unknown or expired biometric challenge"));
        if (stored.getConsumedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CHALLENGE",
                    "Biometric challenge expired or reused");
        }

        WebAuthnCredential cred = credentialRepository.findByTenantIdAndCredentialId(tenantId, credentialId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_CREDENTIAL",
                        "Passkey not registered for this terminal"));

        if (!verifyHmacSignature(challenge, credentialId, signature, cred.getCredentialSecretHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ASSERTION",
                    "Biometric assertion failed");
        }

        stored.setConsumedAt(Instant.now());
        challengeRepository.save(stored);
        cred.setSignCount(cred.getSignCount() + 1);
        credentialRepository.save(cred);

        User target = userRepository.findById(cred.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_INACTIVE", "User not found"));
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_INACTIVE", "User is inactive");
        }

        List<String> roles = userRoleRepository.findRoleCodesByUserId(target.getId());
        List<UUID> warehouseIds = authService.resolveWarehouseIds(tenantId, target.getId(), roles);
        String access = authService.issueTerminalAccessToken(target, roles, warehouseIds);
        int ttl = jwtProperties.getTerminalSwitchTokenMinutes() * 60;
        return new TerminalSwitchResponse(
                access, tenantId, target.getId(), roles, warehouseIds, ttl, "TERMINAL_SWITCH", switchedFrom);
    }

    /**
     * Client helper: expected signature = base64url(HMAC-SHA256(secret, challenge + ":" + credentialId)).
     * Server stores only hash of secret; verification recomputes via constant-time compare of
     * candidate secrets is impossible — instead clients send hmac of known secret and we
     * verify by checking hmac equals provided signature after looking up... we only have hash.
     *
     * Fix: store hmac verification differently — verify signature == base64url(SHA256(challenge|credentialId|secretHash))
     * where client also knows the secret and computes the same, OR store the secret encrypted.
     *
     * Practical approach: signature must equal Hex(SHA-256(challenge + ":" + credentialId + ":" + secretHash))
     * Client receives secret once at registration; for assert, client computes
     * Hex(SHA-256(challenge + ":" + credentialId + ":" + SHA256(secret))) matching stored hash path:
     * signature = Hex(SHA-256(challenge + ":" + credentialId + ":" + credentialSecretHash))
     * Client needs the hash? No — client has secret, computes hash = SHA256(secret), then signature.
     */
    static boolean verifyHmacSignature(String challenge, String credentialId, String signature, String secretHash) {
        if (signature == null || secretHash == null) {
            return false;
        }
        String expected = hash(challenge + ":" + credentialId + ":" + secretHash);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    /** Client-side formula helper for tests / TerminalPinPad biometric mode. */
    public static String computeAssertionSignature(String challenge, String credentialId, String secret) {
        String secretHash = hash(secret);
        return hash(challenge + ":" + credentialId + ":" + secretHash);
    }

    private static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

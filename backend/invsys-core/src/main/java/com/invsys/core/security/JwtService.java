package com.invsys.core.security;

import com.invsys.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Only RS256 asymmetric signatures are accepted — blocks alg:none and HMAC key-confusion. */
    private static final JWSAlgorithm REQUIRED_ALG = JWSAlgorithm.RS256;
    private static final Set<JWSAlgorithm> ALLOWED_ALGS = Set.of(REQUIRED_ALG);
    private static final long CLOCK_SKEW_SECONDS = 30L;
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_TENANT_ID = "tenant_id";
    /** Login surface sandbox: {@code POS} or {@code WMS}. Missing claim = unrestricted (legacy). */
    public static final String CLAIM_APP_CONTEXT = "app_context";
    /** True when the session completed WebAuthn for off-network MFA. */
    public static final String CLAIM_MFA_VERIFIED = "mfa_verified";
    /** True when this WMS session was minted by control-plane owner impersonation. */
    public static final String CLAIM_SUPPORT_IMPERSONATION = "support_impersonation";
    /** Bound session tenant for TERMINAL_SWITCH — must equal {@code tenant_id}. */
    public static final String CLAIM_BIND_TENANT_ID = "bind_tenant_id";
    public static final String TOKEN_TYPE_TERMINAL_SWITCH = "TERMINAL_SWITCH";
    public static final String TOKEN_TYPE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    /** Short-lived Data Plane JWT minted by control-plane Super Admins (support God Mode). */
    public static final String TOKEN_TYPE_IMPERSONATION = "IMPERSONATION";
    public static final String CLAIM_PLATFORM_ADMIN = "platform_admin";
    public static final long IMPERSONATION_TTL_SECONDS = 15L * 60L;

    private final JwtProperties properties;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() throws Exception {
        String privatePem = resolvePem(properties.getPrivateKeyPem(), properties.getPrivateKeyFile());
        String publicPem = resolvePem(properties.getPublicKeyPem(), properties.getPublicKeyFile());

        if (privatePem != null && !privatePem.isBlank() && publicPem != null && !publicPem.isBlank()) {
            privateKey = (RSAPrivateKey) PemUtils.readPrivateKey(privatePem);
            publicKey = (RSAPublicKey) PemUtils.readPublicKey(publicPem);
            log.info("JWT keys loaded from configuration (RS256)");
        } else if (properties.isAllowEphemeral()) {
            log.warn("JWT keys not configured; generating ephemeral RSA keypair for this process");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
        } else {
            throw new IllegalStateException(
                    "JWT keys are required. Set JWT_PRIVATE_KEY/JWT_PUBLIC_KEY (or key files), "
                            + "or set invsys.jwt.allow-ephemeral=true for local/test only.");
        }
    }

    private String resolvePem(String inlinePem, String filePath) throws Exception {
        if (inlinePem != null && !inlinePem.isBlank()) {
            return inlinePem;
        }
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return null;
        }
        return Files.readString(path);
    }

    public String generateAccessToken(UUID userId, UUID tenantId, List<String> roles) {
        return generateAccessToken(userId, tenantId, roles, List.of());
    }

    public String generateAccessToken(UUID userId, UUID tenantId, List<String> roles, List<UUID> warehouseIds) {
        return generateAccessToken(userId, tenantId, roles, warehouseIds, null);
    }

    public String generateAccessToken(UUID userId,
                                      UUID tenantId,
                                      List<String> roles,
                                      List<UUID> warehouseIds,
                                      String appContext) {
        return generateAccessToken(
                userId, tenantId, roles, warehouseIds, properties.getAccessTokenMinutes() * 60L,
                null, appContext, false, false);
    }

    public String generateAccessToken(UUID userId,
                                      UUID tenantId,
                                      List<String> roles,
                                      List<UUID> warehouseIds,
                                      String appContext,
                                      boolean mfaVerified) {
        return generateAccessToken(userId, tenantId, roles, warehouseIds, appContext, mfaVerified, false);
    }

    public String generateAccessToken(UUID userId,
                                      UUID tenantId,
                                      List<String> roles,
                                      List<UUID> warehouseIds,
                                      String appContext,
                                      boolean mfaVerified,
                                      boolean supportImpersonation) {
        return generateAccessToken(
                userId, tenantId, roles, warehouseIds, properties.getAccessTokenMinutes() * 60L,
                null, appContext, mfaVerified, supportImpersonation);
    }

    /**
     * Platform control-plane JWT: {@code SUPER_ADMIN} role, {@code platform_admin=true},
     * {@code token_type=PLATFORM_ADMIN}. No tenant claim — platform admins transcend RLS.
     * Issued only by {@code invsys-admin-api}.
     */
    public String generateAdminAccessToken(UUID platformAdminId) {
        try {
            Instant now = Instant.now();
            long ttlSeconds = properties.getAccessTokenMinutes() * 60L;
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(platformAdminId.toString())
                    .claim("roles", List.of("SUPER_ADMIN"))
                    .claim(CLAIM_PLATFORM_ADMIN, true)
                    .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_PLATFORM_ADMIN)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(REQUIRED_ALG).type(com.nimbusds.jose.JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign admin JWT", e);
        }
    }

    /**
     * Short-lived RS256 JWT for shared-terminal PIN context swap. Bound to a single tenant
     * ({@code tenant_id} + {@code bind_tenant_id}) with a hard TTL from configuration
     * ({@code invsys.jwt.terminal-switch-token-minutes}, default 5).
     */
    public String generateTerminalSwitchToken(UUID userId, UUID tenantId, List<String> roles, List<UUID> warehouseIds) {
        Objects.requireNonNull(tenantId, "tenantId");
        return generateAccessToken(
                userId,
                tenantId,
                roles,
                warehouseIds,
                properties.getTerminalSwitchTokenMinutes() * 60L,
                TOKEN_TYPE_TERMINAL_SWITCH,
                null,
                false,
                false);
    }

    /**
     * Control-plane support impersonation: 15-minute WMS JWT scoped to the target tenant.
     * Consumed via {@code POST /api/v1/auth/impersonation/accept} on the Data Plane.
     */
    public String generateImpersonationAccessToken(UUID userId, UUID tenantId, List<String> roles, List<UUID> warehouseIds) {
        Objects.requireNonNull(tenantId, "tenantId");
        return generateAccessToken(
                userId,
                tenantId,
                roles,
                warehouseIds,
                IMPERSONATION_TTL_SECONDS,
                TOKEN_TYPE_IMPERSONATION,
                "WMS",
                false,
                false);
    }

    private String generateAccessToken(UUID userId,
                                       UUID tenantId,
                                       List<String> roles,
                                       List<UUID> warehouseIds,
                                       long ttlSeconds,
                                       String tokenType,
                                       String appContext,
                                       boolean mfaVerified,
                                       boolean supportImpersonation) {
        try {
            Instant now = Instant.now();
            List<String> warehouseClaim = warehouseIds == null
                    ? List.of()
                    : warehouseIds.stream().map(UUID::toString).toList();
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim(CLAIM_TENANT_ID, tenantId.toString())
                    .claim("roles", roles)
                    .claim("warehouse_ids", warehouseClaim)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)));
            if (tokenType != null) {
                builder.claim(CLAIM_TOKEN_TYPE, tokenType);
            }
            if (appContext != null && !appContext.isBlank()) {
                builder.claim(CLAIM_APP_CONTEXT, appContext.trim());
            }
            if (mfaVerified) {
                builder.claim(CLAIM_MFA_VERIFIED, true);
            }
            if (supportImpersonation) {
                builder.claim(CLAIM_SUPPORT_IMPERSONATION, true);
            }
            if (TOKEN_TYPE_IMPERSONATION.equals(tokenType)) {
                builder.jwtID(UUID.randomUUID().toString());
            }
            if (TOKEN_TYPE_TERMINAL_SWITCH.equals(tokenType)) {
                // Cryptographic same-tenant bind: reject tokens that hop tenants under verification.
                builder.claim(CLAIM_BIND_TENANT_ID, tenantId.toString());
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(REQUIRED_ALG).type(com.nimbusds.jose.JOSEObjectType.JWT).build(),
                    builder.build());
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public String generateSupplierPortalToken(UUID tenantId, UUID purchaseOrderId, long expiryHours) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("supplier-portal")
                    .claim(CLAIM_TENANT_ID, tenantId.toString())
                    .claim("purchase_order_id", purchaseOrderId.toString())
                    .claim(CLAIM_TOKEN_TYPE, "SUPPLIER_PORTAL")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(expiryHours * 3600L)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(REQUIRED_ALG), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign supplier portal JWT", e);
        }
    }

    public SupplierPortalClaims validateSupplierPortalToken(String token) {
        try {
            JWTClaimsSet claims = validateAndParse(token);
            if (!"supplier-portal".equals(claims.getSubject())) {
                throw new IllegalArgumentException("Invalid supplier portal token");
            }
            if (!"SUPPLIER_PORTAL".equals(claims.getClaim(CLAIM_TOKEN_TYPE))) {
                throw new IllegalArgumentException("Invalid supplier portal token type");
            }
            return new SupplierPortalClaims(
                    UUID.fromString(claims.getStringClaim(CLAIM_TENANT_ID)),
                    UUID.fromString(claims.getStringClaim("purchase_order_id")));
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Invalid supplier portal token claims", e);
        }
    }

    public record SupplierPortalClaims(UUID tenantId, UUID purchaseOrderId) {
    }

    public String extractAppContext(String token) {
        Object value = validateAndParse(token).getClaim(CLAIM_APP_CONTEXT);
        if (value == null) {
            return null;
        }
        String appContext = value.toString().trim();
        return appContext.isEmpty() ? null : appContext;
    }

    public boolean extractMfaVerified(String token) {
        Object value = validateAndParse(token).getClaim(CLAIM_MFA_VERIFIED);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    public boolean extractSupportImpersonation(String token) {
        Object value = validateAndParse(token).getClaim(CLAIM_SUPPORT_IMPERSONATION);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * Verifies RS256 signature against the configured public key, rejects {@code alg:none}
     * and symmetric algorithms (HMAC key-confusion), and enforces server-time {@code exp}/{@code iat}.
     * TERMINAL_SWITCH tokens additionally require same-tenant {@code bind_tenant_id} and a TTL
     * capped at {@code terminal-switch-token-minutes}.
     */
    public JWTClaimsSet validateAndParse(String token) {
        try {
            SignedJWT jwt;
            try {
                jwt = SignedJWT.parse(token);
            } catch (java.text.ParseException parseEx) {
                // Unsecured JWTs (alg:none / PlainJWT) and malformed headers fail parse as JWS.
                throw new IllegalArgumentException("JWT algorithm not allowed; RS256 required", parseEx);
            }
            assertAllowedAlgorithm(jwt.getHeader());

            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            assertValidTimeClaims(claims);
            assertTerminalSwitchPolicy(claims);
            return claims;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
        }
    }

    private static void assertAllowedAlgorithm(JWSHeader header) {
        JWSAlgorithm alg = header != null ? header.getAlgorithm() : null;
        if (alg == null || JWSAlgorithm.NONE.equals(alg) || !ALLOWED_ALGS.contains(alg)) {
            throw new IllegalArgumentException("JWT algorithm not allowed; RS256 required");
        }
    }

    private void assertValidTimeClaims(JWTClaimsSet claims) {
        Date exp = claims.getExpirationTime();
        Date iat = claims.getIssueTime();
        if (exp == null || iat == null) {
            throw new IllegalArgumentException("JWT missing required time claims");
        }
        Instant now = Instant.now();
        if (exp.toInstant().isBefore(now.minusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new IllegalArgumentException("JWT expired");
        }
        if (iat.toInstant().isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new IllegalArgumentException("JWT issue time is in the future");
        }
        if (!exp.toInstant().isAfter(iat.toInstant())) {
            throw new IllegalArgumentException("JWT expiration must be after issue time");
        }
    }

    private void assertTerminalSwitchPolicy(JWTClaimsSet claims) throws java.text.ParseException {
        Object tokenType = claims.getClaim(CLAIM_TOKEN_TYPE);
        if (!TOKEN_TYPE_TERMINAL_SWITCH.equals(tokenType)) {
            return;
        }
        String tenantId = claims.getStringClaim(CLAIM_TENANT_ID);
        String bindTenantId = claims.getStringClaim(CLAIM_BIND_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()
                || bindTenantId == null || bindTenantId.isBlank()
                || !tenantId.equals(bindTenantId)) {
            throw new IllegalArgumentException("TERMINAL_SWITCH tenant binding mismatch");
        }
        Instant iat = claims.getIssueTime().toInstant();
        Instant exp = claims.getExpirationTime().toInstant();
        long maxTtlSeconds = properties.getTerminalSwitchTokenMinutes() * 60L + CLOCK_SKEW_SECONDS;
        if (exp.isAfter(iat.plusSeconds(maxTtlSeconds))) {
            throw new IllegalArgumentException("TERMINAL_SWITCH TTL exceeds policy");
        }
    }

    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", "invsys-1",
                "n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
                "e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray())
        )));
    }

    RSAPublicKey getPublicKey() {
        return publicKey;
    }
}

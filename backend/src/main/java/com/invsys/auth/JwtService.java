package com.invsys.auth;

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
import java.util.UUID;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

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
            log.info("JWT keys loaded from configuration");
        } else {
            log.warn("JWT keys not configured; generating ephemeral RSA keypair for this process");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
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
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("tenant_id", tenantId.toString())
                    .claim("roles", roles)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(properties.getAccessTokenMinutes() * 60L)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
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
                    .claim("tenant_id", tenantId.toString())
                    .claim("purchase_order_id", purchaseOrderId.toString())
                    .claim("token_type", "SUPPLIER_PORTAL")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(expiryHours * 3600L)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
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
            if (!"SUPPLIER_PORTAL".equals(claims.getClaim("token_type"))) {
                throw new IllegalArgumentException("Invalid supplier portal token type");
            }
            return new SupplierPortalClaims(
                    UUID.fromString(claims.getStringClaim("tenant_id")),
                    UUID.fromString(claims.getStringClaim("purchase_order_id")));
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Invalid supplier portal token claims", e);
        }
    }

    public record SupplierPortalClaims(UUID tenantId, UUID purchaseOrderId) {
    }

    public JWTClaimsSet validateAndParse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new IllegalArgumentException("JWT expired");
            }
            return claims;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
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

package com.invsys.core.security;

import com.invsys.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cryptographic hardening for terminal-switch JWTs: RS256 only, no alg:none / HMAC confusion,
 * same-tenant bind claim, and strict 5-minute TTL.
 */
class JwtTerminalSwitchCryptoTest {

    private JwtService jwtService;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        publicKey = (RSAPublicKey) pair.getPublic();

        JwtProperties props = new JwtProperties();
        props.setTerminalSwitchTokenMinutes(5);
        props.setAccessTokenMinutes(15);
        jwtService = new JwtService(props);

        var fieldPriv = JwtService.class.getDeclaredField("privateKey");
        fieldPriv.setAccessible(true);
        fieldPriv.set(jwtService, privateKey);
        var fieldPub = JwtService.class.getDeclaredField("publicKey");
        fieldPub.setAccessible(true);
        fieldPub.set(jwtService, publicKey);
    }

    @Test
    void terminalSwitchTokenIsRs256WithBindTenantAndFiveMinuteTtl() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.generateTerminalSwitchToken(userId, tenantId, List.of("PICKER"), List.of());

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);

        JWTClaimsSet claims = jwtService.validateAndParse(token);
        assertThat(claims.getClaim(JwtService.CLAIM_TOKEN_TYPE)).isEqualTo(JwtService.TOKEN_TYPE_TERMINAL_SWITCH);
        assertThat(claims.getStringClaim(JwtService.CLAIM_TENANT_ID)).isEqualTo(tenantId.toString());
        assertThat(claims.getStringClaim(JwtService.CLAIM_BIND_TENANT_ID)).isEqualTo(tenantId.toString());
        long ttlSeconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000L;
        assertThat(ttlSeconds).isEqualTo(300L);
    }

    @Test
    void rejectsAlgNoneUnsecuredJwt() {
        Instant now = Instant.now();
        UUID tenantId = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_BIND_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_TOKEN_TYPE, JwtService.TOKEN_TYPE_TERMINAL_SWITCH)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        // Unsecured JWT (historically alg:none) — must never authenticate.
        String forged = new com.nimbusds.jwt.PlainJWT(claims).serialize();

        assertThatThrownBy(() -> jwtService.validateAndParse(forged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RS256");
    }

    @Test
    void rejectsHs256KeyConfusion() throws Exception {
        byte[] hmacSecret = new byte[32];
        System.arraycopy(publicKey.getEncoded(), 0, hmacSecret, 0, 32);
        Instant now = Instant.now();
        UUID tenantId = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_BIND_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_TOKEN_TYPE, JwtService.TOKEN_TYPE_TERMINAL_SWITCH)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(hmacSecret));

        assertThatThrownBy(() -> jwtService.validateAndParse(jwt.serialize()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm");
    }

    @Test
    void rejectsTerminalSwitchWithMismatchedBindTenant() throws Exception {
        Instant now = Instant.now();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TENANT_ID, tenantA.toString())
                .claim(JwtService.CLAIM_BIND_TENANT_ID, tenantB.toString())
                .claim(JwtService.CLAIM_TOKEN_TYPE, JwtService.TOKEN_TYPE_TERMINAL_SWITCH)
                .claim("roles", List.of("OWNER"))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(privateKey));

        assertThatThrownBy(() -> jwtService.validateAndParse(jwt.serialize()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant binding");
    }

    @Test
    void rejectsTerminalSwitchWhenTtlExceedsFiveMinutes() throws Exception {
        Instant now = Instant.now();
        UUID tenantId = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_BIND_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_TOKEN_TYPE, JwtService.TOKEN_TYPE_TERMINAL_SWITCH)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(privateKey));

        assertThatThrownBy(() -> jwtService.validateAndParse(jwt.serialize()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL");
    }

    @Test
    void rejectsExpiredTerminalSwitchToken() throws Exception {
        Instant now = Instant.now();
        UUID tenantId = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_BIND_TENANT_ID, tenantId.toString())
                .claim(JwtService.CLAIM_TOKEN_TYPE, JwtService.TOKEN_TYPE_TERMINAL_SWITCH)
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.minusSeconds(60)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(privateKey));

        assertThatThrownBy(() -> jwtService.validateAndParse(jwt.serialize()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }
}

package com.invsys.core.security;

import com.invsys.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAppContextClaimTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        JwtProperties props = new JwtProperties();
        props.setAccessTokenMinutes(15);
        jwtService = new JwtService(props);

        var fieldPriv = JwtService.class.getDeclaredField("privateKey");
        fieldPriv.setAccessible(true);
        fieldPriv.set(jwtService, (RSAPrivateKey) pair.getPrivate());
        var fieldPub = JwtService.class.getDeclaredField("publicKey");
        fieldPub.setAccessible(true);
        fieldPub.set(jwtService, (RSAPublicKey) pair.getPublic());
    }

    @Test
    void generateAccessToken_embedsAndExtractsAppContext() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String pos = jwtService.generateAccessToken(userId, tenantId, List.of("OWNER"), List.of(), "POS");
        assertThat(jwtService.extractAppContext(pos)).isEqualTo("POS");
        assertThat(jwtService.validateAndParse(pos).getClaim(JwtService.CLAIM_APP_CONTEXT)).isEqualTo("POS");

        String wms = jwtService.generateAccessToken(userId, tenantId, List.of("OWNER"), List.of(), "WMS");
        assertThat(jwtService.extractAppContext(wms)).isEqualTo("WMS");

        String legacy = jwtService.generateAccessToken(userId, tenantId, List.of("OWNER"), List.of());
        assertThat(jwtService.extractAppContext(legacy)).isNull();
        assertThat(jwtService.extractMfaVerified(legacy)).isFalse();

        String mfa = jwtService.generateAccessToken(userId, tenantId, List.of("OWNER"), List.of(), "WMS", true);
        assertThat(jwtService.extractMfaVerified(mfa)).isTrue();
        assertThat(jwtService.validateAndParse(mfa).getClaim(JwtService.CLAIM_MFA_VERIFIED)).isEqualTo(true);

        String support = jwtService.generateAccessToken(
                userId, tenantId, List.of("OWNER"), List.of(), "WMS", false, true);
        assertThat(jwtService.extractSupportImpersonation(support)).isTrue();
        assertThat(jwtService.validateAndParse(support).getClaim(JwtService.CLAIM_SUPPORT_IMPERSONATION))
                .isEqualTo(true);
    }
}

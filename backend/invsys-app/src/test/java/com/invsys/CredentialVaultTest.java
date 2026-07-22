package com.invsys;

import com.invsys.core.integration.CredentialVaultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialVaultTest extends AbstractIntegrationTest {

    @Autowired CredentialVaultService credentialVaultService;

    @Test
    void encryptDecryptRoundTrip() {
        byte[] plaintext = "shopify-access-token-demo".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = credentialVaultService.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        // Envelope magic ENV1 — local KEK wrap of per-secret DEK
        assertThat(new String(encrypted, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("ENV1");

        byte[] decrypted = credentialVaultService.decrypt(encrypted);
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("shopify-access-token-demo");
    }
}

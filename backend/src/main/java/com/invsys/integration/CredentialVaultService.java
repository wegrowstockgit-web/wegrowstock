package com.invsys.integration;

import com.invsys.config.IntegrationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CredentialVaultService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final byte[] DEV_FALLBACK_KEY = new byte[32];

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialVaultService(IntegrationProperties properties, Environment environment) {
        this.masterKey = resolveMasterKey(properties.getMasterKey(), environment);
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential payload", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(stored);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential payload", e);
        }
    }

    private SecretKey resolveMasterKey(String configured, Environment environment) {
        byte[] keyBytes;
        if (configured != null && !configured.isBlank()) {
            keyBytes = Base64.getDecoder().decode(configured.trim());
        } else if (isDevOrTest(environment)) {
            keyBytes = DEV_FALLBACK_KEY;
        } else {
            throw new IllegalStateException(
                    "INTEGRATION_MASTER_KEY must be set (32-byte value, base64-encoded)");
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("INTEGRATION_MASTER_KEY must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private boolean isDevOrTest(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equals("test") || p.equals("dev"))
                || environment.getActiveProfiles().length == 0;
    }
}

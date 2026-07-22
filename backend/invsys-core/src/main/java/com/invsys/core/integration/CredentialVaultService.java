package com.invsys.core.integration;

import com.invsys.config.IntegrationProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import com.invsys.domain.Tenant;

/**
 * Tenant secret vault: AES-GCM data keys wrapped by AWS KMS, HashiCorp Vault transit,
 * or a local KEK (dev/test). Legacy IV||ciphertext blobs remain decryptable.
 */
@Service
public class CredentialVaultService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final byte[] DEV_FALLBACK_KEY = new byte[32];
    private static final byte[] ENVELOPE_MAGIC = "ENV1".getBytes(StandardCharsets.US_ASCII);
    private static final byte PROVIDER_LOCAL = 0;
    private static final byte PROVIDER_AWS_KMS = 1;
    private static final byte PROVIDER_VAULT = 2;

    private final VaultProvider provider;
    private final SecretKey localKek;
    private final String awsKmsKeyId;
    private final KmsClient kmsClient;
    private final RestClient vaultClient;
    private final String vaultTransitKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialVaultService(IntegrationProperties properties, Environment environment) {
        this.provider = VaultProvider.from(properties.getVaultProvider());
        this.localKek = resolveLocalKek(properties.getMasterKey(), environment);
        this.awsKmsKeyId = properties.getAwsKmsKeyId() == null ? "" : properties.getAwsKmsKeyId().trim();
        this.vaultTransitKey = blankToDefault(properties.getVaultTransitKey(), "invsys-credentials");
        this.kmsClient = provider == VaultProvider.AWS_KMS
                ? KmsClient.builder().region(Region.of(blankToDefault(properties.getAwsRegion(), "us-east-1"))).build()
                : null;
        this.vaultClient = provider == VaultProvider.HASHICORP_VAULT
                ? buildVaultClient(properties)
                : null;
        validateProviderConfig();
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] dek = new byte[32];
            secureRandom.nextBytes(dek);
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] wrappedDek = wrapDek(dek);
            ByteBuffer buffer = ByteBuffer.allocate(
                    ENVELOPE_MAGIC.length + 1 + 2 + wrappedDek.length + iv.length + ciphertext.length);
            buffer.put(ENVELOPE_MAGIC);
            buffer.put(providerCode());
            buffer.putShort((short) wrappedDek.length);
            buffer.put(wrappedDek);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential payload", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        try {
            if (stored == null || stored.length < GCM_IV_LENGTH + 1) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            if (isEnvelope(stored)) {
                return decryptEnvelope(stored);
            }
            return decryptLegacy(stored);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential payload", e);
        }
    }

    private byte[] decryptEnvelope(byte[] stored) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(stored);
        byte[] magic = new byte[ENVELOPE_MAGIC.length];
        buffer.get(magic);
        byte providerCode = buffer.get();
        int wrappedLen = Short.toUnsignedInt(buffer.getShort());
        if (wrappedLen <= 0 || wrappedLen > buffer.remaining() - GCM_IV_LENGTH) {
            throw new IllegalStateException("Corrupt credential envelope");
        }
        byte[] wrappedDek = new byte[wrappedLen];
        buffer.get(wrappedDek);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        byte[] dek = unwrapDek(providerCode, wrappedDek);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(ciphertext);
    }

    private byte[] decryptLegacy(byte[] stored) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(stored);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, localKek, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(ciphertext);
    }

    private byte[] wrapDek(byte[] dek) {
        return switch (provider) {
            case LOCAL -> aesWrap(localKek, dek);
            case AWS_KMS -> kmsWrap(dek);
            case HASHICORP_VAULT -> vaultWrap(dek);
        };
    }

    private byte[] unwrapDek(byte providerCode, byte[] wrapped) {
        return switch (providerCode) {
            case PROVIDER_LOCAL -> aesUnwrap(localKek, wrapped);
            case PROVIDER_AWS_KMS -> kmsUnwrap(wrapped);
            case PROVIDER_VAULT -> vaultUnwrap(wrapped);
            default -> throw new IllegalStateException("Unknown credential vault provider code: " + providerCode);
        };
    }

    private byte[] kmsWrap(byte[] dek) {
        var response = kmsClient.encrypt(EncryptRequest.builder()
                .keyId(awsKmsKeyId)
                .plaintext(SdkBytes.fromByteArray(dek))
                .build());
        return response.ciphertextBlob().asByteArray();
    }

    private byte[] kmsUnwrap(byte[] wrapped) {
        var response = kmsClient.decrypt(DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(wrapped))
                .build());
        return response.plaintext().asByteArray();
    }

    @SuppressWarnings("unchecked")
    private byte[] vaultWrap(byte[] dek) {
        String plaintext = Base64.getEncoder().encodeToString(dek);
        Map<String, Object> body = Map.of("plaintext", plaintext);
        Map<String, Object> response = vaultClient.post()
                .uri("/v1/transit/encrypt/{key}", vaultTransitKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map<?, ?> dataMap) || dataMap.get("ciphertext") == null) {
            throw new IllegalStateException("Vault transit encrypt returned no ciphertext");
        }
        return dataMap.get("ciphertext").toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private byte[] vaultUnwrap(byte[] wrapped) {
        String ciphertext = new String(wrapped, StandardCharsets.UTF_8);
        Map<String, Object> body = Map.of("ciphertext", ciphertext);
        Map<String, Object> response = vaultClient.post()
                .uri("/v1/transit/decrypt/{key}", vaultTransitKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map<?, ?> dataMap) || dataMap.get("plaintext") == null) {
            throw new IllegalStateException("Vault transit decrypt returned no plaintext");
        }
        return Base64.getDecoder().decode(dataMap.get("plaintext").toString());
    }

    private static byte[] aesWrap(SecretKey kek, byte[] dek) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(dek);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to wrap data key", e);
        }
    }

    private static byte[] aesUnwrap(SecretKey kek, byte[] wrapped) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(wrapped);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to unwrap data key", e);
        }
    }

    private byte providerCode() {
        return switch (provider) {
            case LOCAL -> PROVIDER_LOCAL;
            case AWS_KMS -> PROVIDER_AWS_KMS;
            case HASHICORP_VAULT -> PROVIDER_VAULT;
        };
    }

    private static boolean isEnvelope(byte[] stored) {
        if (stored.length < ENVELOPE_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ENVELOPE_MAGIC.length; i++) {
            if (stored[i] != ENVELOPE_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private void validateProviderConfig() {
        if (provider == VaultProvider.AWS_KMS && awsKmsKeyId.isBlank()) {
            throw new IllegalStateException("invsys.integration.aws-kms-key-id is required when vault-provider=AWS_KMS");
        }
        if (provider == VaultProvider.HASHICORP_VAULT && vaultClient == null) {
            throw new IllegalStateException("Vault client failed to initialize");
        }
    }

    private static RestClient buildVaultClient(IntegrationProperties properties) {
        String address = properties.getVaultAddress() == null ? "" : properties.getVaultAddress().trim();
        String token = properties.getVaultToken() == null ? "" : properties.getVaultToken().trim();
        if (address.isBlank() || token.isBlank()) {
            throw new IllegalStateException(
                    "invsys.integration.vault-address and vault-token are required when vault-provider=HASHICORP_VAULT");
        }
        String base = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        return RestClient.builder()
                .baseUrl(base)
                .defaultHeader("X-Vault-Token", token)
                .build();
    }

    private SecretKey resolveLocalKek(String configured, Environment environment) {
        if (configured != null && !configured.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(configured.trim());
            if (keyBytes.length != 32) {
                throw new IllegalStateException("INTEGRATION_MASTER_KEY must decode to exactly 32 bytes");
            }
            return new SecretKeySpec(keyBytes, "AES");
        }
        if (isDevOrTest(environment)) {
            return new SecretKeySpec(DEV_FALLBACK_KEY, "AES");
        }
        if (provider == VaultProvider.LOCAL) {
            throw new IllegalStateException(
                    "INTEGRATION_MASTER_KEY must be set (32-byte value, base64-encoded)");
        }
        // External providers: local KEK only for legacy IV||ciphertext decrypt during migration.
        return new SecretKeySpec(DEV_FALLBACK_KEY, "AES");
    }

    private boolean isDevOrTest(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equals("test") || p.equals("dev"))
                || environment.getActiveProfiles().length == 0;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    enum VaultProvider {
        LOCAL,
        AWS_KMS,
        HASHICORP_VAULT;

        static VaultProvider from(String raw) {
            if (raw == null || raw.isBlank()) {
                return LOCAL;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return switch (normalized) {
                case "LOCAL", "APP", "STATIC" -> LOCAL;
                case "AWS_KMS", "KMS" -> AWS_KMS;
                case "HASHICORP_VAULT", "VAULT", "TRANSIT" -> HASHICORP_VAULT;
                default -> throw new IllegalStateException(
                        "Unknown invsys.integration.vault-provider: " + raw);
            };
        }
    }
}

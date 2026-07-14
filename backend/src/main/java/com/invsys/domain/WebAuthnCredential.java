package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "webauthn_credentials")
public class WebAuthnCredential extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", nullable = false, length = 512)
    private String credentialId;

    @Column(name = "public_key_pem", columnDefinition = "text")
    private String publicKeyPem;

    @Column(name = "credential_secret_hash", length = 128)
    private String credentialSecretHash;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(length = 255)
    private String label;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getCredentialSecretHash() {
        return credentialSecretHash;
    }

    public void setCredentialSecretHash(String credentialSecretHash) {
        this.credentialSecretHash = credentialSecretHash;
    }

    public long getSignCount() {
        return signCount;
    }

    public void setSignCount(long signCount) {
        this.signCount = signCount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}

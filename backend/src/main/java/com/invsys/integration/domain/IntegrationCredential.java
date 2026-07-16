package com.invsys.integration.domain;

import com.invsys.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "integration_credentials")
public class IntegrationCredential extends TenantScopedEntity {

    @Column(nullable = false, length = 50)
    private String system;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] ciphertext;

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Column(nullable = false, length = 30)
    private String status = "CONNECTED";

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(byte[] ciphertext) {
        this.ciphertext = ciphertext;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(int keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public void setRefreshTokenExpiresAt(Instant refreshTokenExpiresAt) {
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }
}

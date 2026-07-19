package com.invsys.domain;

import com.invsys.integration.channel.IntegrationChannelStatus;
import com.invsys.integration.channel.IntegrationChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "integration_channels")
public class IntegrationChannel extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 30)
    private IntegrationChannelType channelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationChannelStatus status = IntegrationChannelStatus.DISCONNECTED;

    @Column(name = "encrypted_credentials", columnDefinition = "bytea")
    private byte[] encryptedCredentials;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settings = new LinkedHashMap<>();

    /**
     * Plaintext credential bag used only during save/load via {@code IntegrationChannelService}.
     * Never persisted.
     */
    @Transient
    private Map<String, String> credentialSecrets;

    public IntegrationChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(IntegrationChannelType channelType) {
        this.channelType = channelType;
    }

    public IntegrationChannelStatus getStatus() {
        return status;
    }

    public void setStatus(IntegrationChannelStatus status) {
        this.status = status;
    }

    public byte[] getEncryptedCredentials() {
        return encryptedCredentials;
    }

    public void setEncryptedCredentials(byte[] encryptedCredentials) {
        this.encryptedCredentials = encryptedCredentials;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new LinkedHashMap<>();
    }

    public Map<String, String> getCredentialSecrets() {
        return credentialSecrets;
    }

    public void setCredentialSecrets(Map<String, String> credentialSecrets) {
        this.credentialSecrets = credentialSecrets;
    }

    public boolean hasEncryptedCredentials() {
        return encryptedCredentials != null && encryptedCredentials.length > 0;
    }
}

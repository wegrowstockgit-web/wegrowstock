package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "channel_integrations")
public class ChannelIntegration extends TenantScopedEntity {

    @Column(nullable = false)
    private String platform;

    @Column(name = "shop_identifier", nullable = false)
    private String shopIdentifier;

    @Column(name = "credential_id")
    private UUID credentialId;

    @Column(nullable = false)
    private String status = "ACTIVE";

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getShopIdentifier() {
        return shopIdentifier;
    }

    public void setShopIdentifier(String shopIdentifier) {
        this.shopIdentifier = shopIdentifier;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(UUID credentialId) {
        this.credentialId = credentialId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

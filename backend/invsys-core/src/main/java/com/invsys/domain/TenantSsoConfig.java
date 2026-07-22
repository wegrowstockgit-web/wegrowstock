package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "tenant_sso_configs")
public class TenantSsoConfig extends TenantScopedEntity {

    @Column(name = "issuer_url", nullable = false)
    private String issuerUrl;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "encrypted_client_secret", nullable = false, columnDefinition = "bytea")
    private byte[] encryptedClientSecret;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "force_sso", nullable = false)
    private boolean forceSso;

    @Column(nullable = false)
    private String protocol = "OIDC";

    @Column(name = "saml_metadata_url")
    private String samlMetadataUrl;

    @Column(name = "saml_entity_id")
    private String samlEntityId;

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public void setIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public byte[] getEncryptedClientSecret() {
        return encryptedClientSecret;
    }

    public void setEncryptedClientSecret(byte[] encryptedClientSecret) {
        this.encryptedClientSecret = encryptedClientSecret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isForceSso() {
        return forceSso;
    }

    public void setForceSso(boolean forceSso) {
        this.forceSso = forceSso;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getSamlMetadataUrl() {
        return samlMetadataUrl;
    }

    public void setSamlMetadataUrl(String samlMetadataUrl) {
        this.samlMetadataUrl = samlMetadataUrl;
    }

    public String getSamlEntityId() {
        return samlEntityId;
    }

    public void setSamlEntityId(String samlEntityId) {
        this.samlEntityId = samlEntityId;
    }
}

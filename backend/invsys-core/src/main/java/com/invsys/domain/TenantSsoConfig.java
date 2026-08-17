package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "sso_provider", nullable = false, length = 32)
    private String ssoProvider = "CUSTOM";

    @Column(name = "acs_url", length = 1024)
    private String acsUrl;

    @Column(name = "saml_certificate", columnDefinition = "text")
    private String samlCertificate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "corporate_cidr_ips", columnDefinition = "jsonb", nullable = false)
    private List<String> corporateCidrIps = new ArrayList<>();

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

    public String getSsoProvider() {
        return ssoProvider;
    }

    public void setSsoProvider(String ssoProvider) {
        this.ssoProvider = ssoProvider != null && !ssoProvider.isBlank() ? ssoProvider : "CUSTOM";
    }

    public String getAcsUrl() {
        return acsUrl;
    }

    public void setAcsUrl(String acsUrl) {
        this.acsUrl = acsUrl;
    }

    public String getSamlCertificate() {
        return samlCertificate;
    }

    public void setSamlCertificate(String samlCertificate) {
        this.samlCertificate = samlCertificate;
    }

    public List<String> getCorporateCidrIps() {
        return corporateCidrIps;
    }

    public void setCorporateCidrIps(List<String> corporateCidrIps) {
        this.corporateCidrIps = corporateCidrIps != null ? corporateCidrIps : new ArrayList<>();
    }

    /**
     * Internal-network CIDRs for conditional access. Same store as {@link #corporateCidrIps}
     * so Home Realm Discovery and the access gateway share one allowlist.
     */
    public List<String> getAllowedCidrBlocks() {
        return getCorporateCidrIps();
    }

    public void setAllowedCidrBlocks(List<String> allowedCidrBlocks) {
        setCorporateCidrIps(allowedCidrBlocks);
    }
}

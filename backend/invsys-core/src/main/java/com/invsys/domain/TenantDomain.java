package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "tenant_domains")
public class TenantDomain extends TenantScopedEntity {

    @Column(name = "domain_name", nullable = false)
    private String domainName;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dkim_tokens", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, String>> dkimTokens = new ArrayList<>();

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public List<Map<String, String>> getDkimTokens() {
        return dkimTokens;
    }

    public void setDkimTokens(List<Map<String, String>> dkimTokens) {
        this.dkimTokens = dkimTokens;
    }
}

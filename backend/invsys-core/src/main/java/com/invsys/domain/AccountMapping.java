package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "account_mappings")
public class AccountMapping extends TenantScopedEntity {

    @Column(nullable = false)
    private String system;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getExternalAccountId() {
        return externalAccountId;
    }

    public void setExternalAccountId(String externalAccountId) {
        this.externalAccountId = externalAccountId;
    }
}

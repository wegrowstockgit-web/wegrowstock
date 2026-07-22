package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "roles")
public class Role extends TenantScopedEntity {

    @Column(nullable = false)
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

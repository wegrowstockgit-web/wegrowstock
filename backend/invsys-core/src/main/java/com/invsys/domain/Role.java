package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "roles")
public class Role extends TenantScopedEntity {

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "network_access_level", nullable = false, length = 32)
    private NetworkAccessLevel networkAccessLevel = NetworkAccessLevel.STRICT_INTERNAL;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public NetworkAccessLevel getNetworkAccessLevel() {
        return networkAccessLevel == null ? NetworkAccessLevel.STRICT_INTERNAL : networkAccessLevel;
    }

    public void setNetworkAccessLevel(NetworkAccessLevel networkAccessLevel) {
        this.networkAccessLevel = networkAccessLevel == null
                ? NetworkAccessLevel.STRICT_INTERNAL
                : networkAccessLevel;
    }
}

package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.invsys.core.common.TenantScopedEntity;

import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role extends TenantScopedEntity {

    public static final Set<String> SYSTEM_CODES = Set.of(
            "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER",
            "RETAIL_CASHIER", "RETAIL_MANAGER", "B2B_CUSTOMER", "SUPPLIER");

    @Column(nullable = false, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "network_access_level", nullable = false, length = 32)
    private NetworkAccessLevel networkAccessLevel = NetworkAccessLevel.STRICT_INTERNAL;

    @Column(name = "is_system_role", nullable = false)
    private boolean systemRole = false;

    public static boolean isReservedSystemCode(String code) {
        return code != null && SYSTEM_CODES.contains(code.toUpperCase(Locale.ROOT));
    }

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

    public boolean isSystemRole() {
        return systemRole;
    }

    public void setSystemRole(boolean systemRole) {
        this.systemRole = systemRole;
    }
}

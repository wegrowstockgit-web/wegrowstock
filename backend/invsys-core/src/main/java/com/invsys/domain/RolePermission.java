package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.invsys.core.common.TenantScopedEntity;

import java.util.UUID;

@Entity
@Table(name = "role_permissions")
public class RolePermission extends TenantScopedEntity {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(nullable = false)
    private boolean granted = true;

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public String getPermissionKey() {
        return permissionKey;
    }

    public void setPermissionKey(String permissionKey) {
        this.permissionKey = permissionKey;
    }

    public boolean isGranted() {
        return granted;
    }

    public void setGranted(boolean granted) {
        this.granted = granted;
    }
}

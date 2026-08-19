package com.invsys.repository;

import com.invsys.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    Optional<RolePermission> findByTenantIdAndRoleIdAndPermissionKey(
            UUID tenantId, UUID roleId, String permissionKey);

    List<RolePermission> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
            UUID tenantId, List<UUID> roleIds, String permissionKey);

    boolean existsByTenantIdAndRoleIdInAndPermissionKey(
            UUID tenantId, List<UUID> roleIds, String permissionKey);

    void deleteByTenantIdAndRoleId(UUID tenantId, UUID roleId);
}

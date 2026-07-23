package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.Role;
import com.invsys.domain.RolePermission;
import com.invsys.repository.RolePermissionRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;

    public RolePermissionService(RolePermissionRepository rolePermissionRepository,
                                   RoleRepository roleRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
    }

    public List<RolePermissionRow> listForTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return rolePermissionRepository.findByTenantId(tenantId).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public RolePermissionRow upsert(UpsertRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found"));
        if (!tenantId.equals(role.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found");
        }

        RolePermission permission = rolePermissionRepository
                .findByTenantIdAndRoleIdAndPermissionKey(tenantId, request.roleId(), request.permissionKey())
                .orElseGet(() -> {
                    RolePermission created = new RolePermission();
                    created.setTenantId(tenantId);
                    created.setRoleId(request.roleId());
                    created.setPermissionKey(request.permissionKey());
                    return created;
                });
        permission.setGranted(request.granted());
        permission = rolePermissionRepository.save(permission);
        return toRow(permission);
    }

    public boolean isGrantedForRoles(List<UUID> roleIds, String permissionKey) {
        if (roleIds.isEmpty()) {
            return true;
        }
        UUID tenantId = TenantContext.requireTenantId();
        if (!rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKey(
                tenantId, roleIds, permissionKey)) {
            return true;
        }
        return rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
                tenantId, roleIds, permissionKey);
    }

    private RolePermissionRow toRow(RolePermission permission) {
        String roleCode = roleRepository.findById(permission.getRoleId())
                .map(Role::getCode)
                .orElse(null);
        return new RolePermissionRow(
                permission.getId(),
                permission.getRoleId(),
                roleCode,
                permission.getPermissionKey(),
                permission.isGranted(),
                permission.getUpdatedAt());
    }

    public record UpsertRequest(UUID roleId, String permissionKey, boolean granted) {
    }

    public record RolePermissionRow(
            UUID id,
            UUID roleId,
            String roleCode,
            String permissionKey,
            boolean granted,
            java.time.Instant updatedAt
    ) {
    }
}

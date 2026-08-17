package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.security.PermissionKeys;
import com.invsys.domain.Role;
import com.invsys.domain.RolePermission;
import com.invsys.repository.RolePermissionRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Union evaluation: {@code true} if ANY of the given roles has {@code granted=true}
     * for the permission key.
     */
    public boolean isGrantedForRoles(List<UUID> roleIds, String permissionKey) {
        if (roleIds == null || roleIds.isEmpty() || permissionKey == null || permissionKey.isBlank()) {
            return false;
        }
        UUID tenantId = TenantContext.requireTenantId();
        return rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
                tenantId, roleIds, permissionKey);
    }

    /**
     * Union of all {@code granted=true} permission keys across the given role codes.
     * OWNER receives the full catalog.
     */
    @Transactional(readOnly = true)
    public List<String> resolveGrantedPermissions(UUID tenantId, List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        if (roleCodes.contains("OWNER")) {
            return List.copyOf(PermissionKeys.CATALOG);
        }
        List<UUID> roleIds = new ArrayList<>();
        for (String code : roleCodes) {
            roleRepository.findByTenantIdAndCode(tenantId, code)
                    .map(Role::getId)
                    .ifPresent(roleIds::add);
        }
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<String> granted = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            for (RolePermission row : rolePermissionRepository.findByTenantIdAndRoleId(tenantId, roleId)) {
                if (row.isGranted()) {
                    granted.add(row.getPermissionKey());
                }
            }
        }
        return List.copyOf(granted);
    }

    /**
     * Seeds the baseline matrix for a newly provisioned tenant (idempotent).
     */
    @Transactional
    public void seedBaselineForTenant(UUID tenantId, List<Role> roles) {
        for (Role role : roles) {
            for (String key : PermissionKeys.CATALOG) {
                boolean granted = baselineGranted(role.getCode(), key);
                RolePermission existing = rolePermissionRepository
                        .findByTenantIdAndRoleIdAndPermissionKey(tenantId, role.getId(), key)
                        .orElse(null);
                if (existing != null) {
                    continue;
                }
                RolePermission created = new RolePermission();
                created.setTenantId(tenantId);
                created.setRoleId(role.getId());
                created.setPermissionKey(key);
                created.setGranted(granted);
                rolePermissionRepository.save(created);
            }
        }
    }

    static boolean baselineGranted(String roleCode, String permissionKey) {
        if ("OWNER".equals(roleCode) || "ADMIN".equals(roleCode)) {
            return true;
        }
        if ("WAREHOUSE_MANAGER".equals(roleCode)) {
            return Set.of(
                    PermissionKeys.INVENTORY_COST_VIEW,
                    PermissionKeys.INVENTORY_ADJUST,
                    PermissionKeys.FULFILLMENT_OVERRIDE,
                    PermissionKeys.RETURNS_QC_PROCESS,
                    PermissionKeys.MRP_RUN,
                    PermissionKeys.PRINTING_THERMAL,
                    PermissionKeys.PURCHASING_PO_APPROVE,
                    PermissionKeys.POS_OPERATE,
                    PermissionKeys.POS_SUPERVISE
            ).contains(permissionKey);
        }
        if ("RETAIL_MANAGER".equals(roleCode)) {
            return PermissionKeys.POS_OPERATE.equals(permissionKey)
                    || PermissionKeys.POS_SUPERVISE.equals(permissionKey);
        }
        if ("RETAIL_CASHIER".equals(roleCode)) {
            return PermissionKeys.POS_OPERATE.equals(permissionKey);
        }
        if ("PICKER".equals(roleCode)) {
            return PermissionKeys.PRINTING_THERMAL.equals(permissionKey);
        }
        return false;
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

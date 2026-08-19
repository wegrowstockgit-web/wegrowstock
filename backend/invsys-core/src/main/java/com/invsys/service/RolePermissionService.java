package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.common.exception.SystemRoleLockedException;
import com.invsys.core.security.PermissionKeys;
import com.invsys.domain.NetworkAccessLevel;
import com.invsys.domain.Role;
import com.invsys.domain.RolePermission;
import com.invsys.repository.RolePermissionRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RolePermissionService {

    private static final int MAX_ROLE_CODE_LENGTH = 80;

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;

    public RolePermissionService(RolePermissionRepository rolePermissionRepository,
                                   RoleRepository roleRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
    }

    public List<Role> listRoles() {
        return roleRepository.findByTenantId(TenantContext.requireTenantId());
    }

    public List<RolePermissionRow> listForTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return rolePermissionRepository.findByTenantId(tenantId).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public RolePermissionRow upsert(UpsertRequest request) {
        Role role = requireTenantRole(request.roleId());
        requireMutable(role);

        UUID tenantId = role.getTenantId();
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

    @Transactional
    public Role createCustomRole(String name, UUID cloneFromRoleId) {
        return createCustomRole(name, cloneFromRoleId, null);
    }

    @Transactional
    public Role createCustomRole(String name, UUID cloneFromRoleId, String description) {
        UUID tenantId = TenantContext.requireTenantId();
        String code = slugifyRoleCode(name);
        if (code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Role name is required");
        }
        if (Role.isReservedSystemCode(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE",
                    "Cannot create a role that shadows a system role");
        }
        if (roleRepository.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_EXISTS", "A role with this name already exists");
        }

        Role cloneSource = null;
        if (cloneFromRoleId != null) {
            cloneSource = requireTenantRole(cloneFromRoleId);
        }

        Role created = new Role();
        created.setTenantId(tenantId);
        created.setCode(code);
        created.setSystemRole(false);
        created.setNetworkAccessLevel(NetworkAccessLevel.STRICT_INTERNAL);
        created.setDescription(normalizeDescription(description));
        created = roleRepository.save(created);

        Map<String, Boolean> grants = new LinkedHashMap<>();
        for (String key : PermissionKeys.CATALOG) {
            grants.put(key, false);
        }
        if (cloneSource != null) {
            for (RolePermission row : rolePermissionRepository.findByTenantIdAndRoleId(tenantId, cloneSource.getId())) {
                grants.put(row.getPermissionKey(), row.isGranted());
            }
        }
        persistGrants(created, grants);
        return created;
    }

    @Transactional
    public List<RolePermissionRow> replacePermissions(UUID roleId, List<PermissionGrant> grants) {
        Role role = requireTenantRole(roleId);
        requireMutable(role);
        Map<String, Boolean> next = new LinkedHashMap<>();
        for (String key : PermissionKeys.CATALOG) {
            next.put(key, false);
        }
        if (grants != null) {
            for (PermissionGrant grant : grants) {
                if (grant == null || grant.permissionKey() == null || grant.permissionKey().isBlank()) {
                    continue;
                }
                if (!PermissionKeys.CATALOG.contains(grant.permissionKey())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION",
                            "Unknown permission: " + grant.permissionKey());
                }
                next.put(grant.permissionKey(), grant.granted());
            }
        }
        persistGrants(role, next);
        return rolePermissionRepository.findByTenantIdAndRoleId(role.getTenantId(), role.getId()).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public void deleteCustomRole(UUID roleId) {
        Role role = requireTenantRole(roleId);
        requireMutable(role);
        rolePermissionRepository.deleteByTenantIdAndRoleId(role.getTenantId(), role.getId());
        roleRepository.delete(role);
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

    static String slugifyRoleCode(String name) {
        if (name == null) {
            return "";
        }
        String slug = name.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.length() > MAX_ROLE_CODE_LENGTH) {
            slug = slug.substring(0, MAX_ROLE_CODE_LENGTH).replaceAll("_+$", "");
        }
        return slug;
    }

    static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return Role.CUSTOM_ROLE_FALLBACK;
        }
        String trimmed = description.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private void persistGrants(Role role, Map<String, Boolean> grants) {
        UUID tenantId = role.getTenantId();
        for (Map.Entry<String, Boolean> entry : grants.entrySet()) {
            RolePermission permission = rolePermissionRepository
                    .findByTenantIdAndRoleIdAndPermissionKey(tenantId, role.getId(), entry.getKey())
                    .orElseGet(() -> {
                        RolePermission created = new RolePermission();
                        created.setTenantId(tenantId);
                        created.setRoleId(role.getId());
                        created.setPermissionKey(entry.getKey());
                        return created;
                    });
            permission.setGranted(Boolean.TRUE.equals(entry.getValue()));
            rolePermissionRepository.save(permission);
        }
    }

    private Role requireTenantRole(UUID roleId) {
        UUID tenantId = TenantContext.requireTenantId();
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found"));
        if (!tenantId.equals(role.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found");
        }
        return role;
    }

    private static void requireMutable(Role role) {
        if (role.isSystemRole() || Role.isReservedSystemCode(role.getCode())) {
            throw new SystemRoleLockedException();
        }
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

    public record PermissionGrant(String permissionKey, boolean granted) {
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

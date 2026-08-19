package com.invsys.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invsys.core.common.ApiException;
import com.invsys.core.security.PermissionKeys;
import com.invsys.domain.NetworkAccessLevel;
import com.invsys.domain.Role;
import com.invsys.repository.RoleRepository;
import com.invsys.service.RolePermissionService;
import com.invsys.service.SsoConfigService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Granular RBAC matrix admin API plus tenant-scoped custom role CRUD.
 * Canonical matrix path: {@code /api/v1/settings/permissions}.
 * Custom roles: {@code /api/v1/roles}.
 * Legacy alias: {@code /api/v1/settings/role-permissions}.
 */
@RestController
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;
    private final RoleRepository roleRepository;
    private final SsoConfigService ssoConfigService;

    public RolePermissionController(RolePermissionService rolePermissionService,
                                    RoleRepository roleRepository,
                                    SsoConfigService ssoConfigService) {
        this.rolePermissionService = rolePermissionService;
        this.roleRepository = roleRepository;
        this.ssoConfigService = ssoConfigService;
    }

    @GetMapping({"/api/v1/settings/permissions", "/api/v1/settings/role-permissions"})
    public MatrixResponse list() {
        UUID tenantId = TenantContext.requireTenantId();
        List<RoleDefinition> roles = roleRepository.findByTenantId(tenantId).stream()
                .map(RolePermissionController::toDefinition)
                .toList();
        List<RolePermissionService.RolePermissionRow> rows = rolePermissionService.listForTenant();
        List<Grant> grants = rows.stream()
                .map(r -> new Grant(r.roleId(), r.permissionKey(), r.granted()))
                .toList();
        List<String> cidrs = ssoConfigService.getForCurrentTenant()
                .map(SsoConfigService.SsoConfigView::corporateCidrIps)
                .orElse(List.of());
        return new MatrixResponse(roles, PermissionKeys.CATALOG, grants, cidrs);
    }

    @PatchMapping({"/api/v1/settings/permissions", "/api/v1/settings/role-permissions"})
    public RolePermissionService.RolePermissionRow patch(@Valid @RequestBody UpsertBody body) {
        return rolePermissionService.upsert(new RolePermissionService.UpsertRequest(
                body.roleId(), body.permissionKey(), body.granted()));
    }

    /** @deprecated prefer {@link #patch(UpsertBody)} */
    @PutMapping({"/api/v1/settings/permissions", "/api/v1/settings/role-permissions"})
    public RolePermissionService.RolePermissionRow upsert(@Valid @RequestBody UpsertBody body) {
        return patch(body);
    }

    @PatchMapping({
            "/api/v1/settings/permissions/network-access",
            "/api/v1/settings/role-permissions/network-access"
    })
    public RoleDefinition patchNetworkAccess(@Valid @RequestBody NetworkAccessBody body) {
        UUID tenantId = TenantContext.requireTenantId();
        Role role = roleRepository.findById(body.roleId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found"));
        if (!tenantId.equals(role.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found");
        }
        role.setNetworkAccessLevel(NetworkAccessLevel.fromCode(body.networkAccessLevel()));
        role = roleRepository.save(role);
        return toDefinition(role);
    }

    @PatchMapping({
            "/api/v1/settings/permissions/allowed-cidrs",
            "/api/v1/settings/role-permissions/allowed-cidrs"
    })
    public CidrResponse patchAllowedCidrs(@RequestBody CidrBody body) {
        List<String> cidrs = ssoConfigService.replaceAllowedCidrs(
                body == null ? List.of() : body.allowedCidrBlocks());
        return new CidrResponse(cidrs);
    }

    @GetMapping({
            "/api/v1/settings/permissions/catalog",
            "/api/v1/settings/role-permissions/catalog"
    })
    public List<String> catalog() {
        return PermissionKeys.CATALOG;
    }

    @GetMapping("/api/v1/roles")
    public List<RoleDefinition> listRoles() {
        return rolePermissionService.listRoles().stream()
                .map(RolePermissionController::toDefinition)
                .toList();
    }

    @PostMapping("/api/v1/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDefinition createRole(@Valid @RequestBody CreateRoleBody body) {
        Role created = rolePermissionService.createCustomRole(body.name(), body.cloneFromRoleId(), body.description());
        return toDefinition(created);
    }

    @PutMapping("/api/v1/roles/{roleId}/permissions")
    public List<RolePermissionService.RolePermissionRow> replacePermissions(
            @PathVariable UUID roleId,
            @RequestBody ReplacePermissionsBody body) {
        List<RolePermissionService.PermissionGrant> grants = body == null || body.grants() == null
                ? List.of()
                : body.grants().stream()
                .map(g -> new RolePermissionService.PermissionGrant(g.permissionKey(), g.granted()))
                .toList();
        return rolePermissionService.replacePermissions(roleId, grants);
    }

    @DeleteMapping("/api/v1/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID roleId) {
        rolePermissionService.deleteCustomRole(roleId);
    }

    static RoleDefinition toDefinition(Role role) {
        boolean systemRole = role.isSystemRole() || Role.isReservedSystemCode(role.getCode());
        return new RoleDefinition(
                role.getId(),
                role.getCode(),
                role.getNetworkAccessLevel().name(),
                systemRole,
                role.getDescription());
    }

    public record CreateRoleBody(
            @NotBlank String name,
            UUID cloneFromRoleId,
            @Size(max = 255) String description
    ) {
    }

    public record ReplacePermissionsBody(List<PermissionGrantBody> grants) {
    }

    public record PermissionGrantBody(
            @NotBlank String permissionKey,
            boolean granted
    ) {
    }

    public record UpsertBody(
            @NotNull UUID roleId,
            @NotBlank String permissionKey,
            boolean granted
    ) {
    }

    public record NetworkAccessBody(
            @NotNull UUID roleId,
            @NotBlank String networkAccessLevel
    ) {
    }

    public record CidrBody(List<String> allowedCidrBlocks) {
    }

    public record CidrResponse(List<String> allowedCidrBlocks) {
    }

    public record RoleDefinition(
            UUID id,
            String name,
            String networkAccessLevel,
            @JsonProperty("isSystemRole") boolean systemRole,
            String description
    ) {
        public RoleDefinition(UUID id, String name) {
            this(id, name, NetworkAccessLevel.STRICT_INTERNAL.name(), false, null);
        }

        public RoleDefinition(UUID id, String name, String networkAccessLevel) {
            this(id, name, networkAccessLevel, false, null);
        }

        public RoleDefinition(UUID id, String name, String networkAccessLevel, boolean systemRole) {
            this(id, name, networkAccessLevel, systemRole, null);
        }
    }

    public record Grant(UUID roleId, String permissionKey, boolean granted) {
    }

    public record MatrixResponse(
            List<RoleDefinition> roles,
            List<String> permissionKeys,
            List<Grant> grants,
            List<String> allowedCidrBlocks
    ) {
        public MatrixResponse(List<RoleDefinition> roles, List<String> permissionKeys, List<Grant> grants) {
            this(roles, permissionKeys, grants, List.of());
        }
    }
}

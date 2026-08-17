package com.invsys.api;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.invsys.core.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Granular RBAC matrix admin API.
 * Canonical path: {@code /api/v1/settings/permissions}.
 * Legacy alias: {@code /api/v1/settings/role-permissions}.
 */
@RestController
@RequestMapping({"/api/v1/settings/permissions", "/api/v1/settings/role-permissions"})
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

    @GetMapping
    public MatrixResponse list() {
        UUID tenantId = TenantContext.requireTenantId();
        List<RoleDefinition> roles = roleRepository.findByTenantId(tenantId).stream()
                .map(r -> new RoleDefinition(r.getId(), r.getCode(), r.getNetworkAccessLevel().name()))
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

    @PatchMapping
    public RolePermissionService.RolePermissionRow patch(@Valid @RequestBody UpsertBody body) {
        return rolePermissionService.upsert(new RolePermissionService.UpsertRequest(
                body.roleId(), body.permissionKey(), body.granted()));
    }

    /** @deprecated prefer {@link #patch(UpsertBody)} */
    @PutMapping
    public RolePermissionService.RolePermissionRow upsert(@Valid @RequestBody UpsertBody body) {
        return patch(body);
    }

    @PatchMapping("/network-access")
    public RoleDefinition patchNetworkAccess(@Valid @RequestBody NetworkAccessBody body) {
        UUID tenantId = TenantContext.requireTenantId();
        Role role = roleRepository.findById(body.roleId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found"));
        if (!tenantId.equals(role.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Role not found");
        }
        role.setNetworkAccessLevel(NetworkAccessLevel.fromCode(body.networkAccessLevel()));
        role = roleRepository.save(role);
        return new RoleDefinition(role.getId(), role.getCode(), role.getNetworkAccessLevel().name());
    }

    @PatchMapping("/allowed-cidrs")
    public CidrResponse patchAllowedCidrs(@RequestBody CidrBody body) {
        List<String> cidrs = ssoConfigService.replaceAllowedCidrs(
                body == null ? List.of() : body.allowedCidrBlocks());
        return new CidrResponse(cidrs);
    }

    @GetMapping("/catalog")
    public List<String> catalog() {
        return PermissionKeys.CATALOG;
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

    public record RoleDefinition(UUID id, String name, String networkAccessLevel) {
        public RoleDefinition(UUID id, String name) {
            this(id, name, NetworkAccessLevel.STRICT_INTERNAL.name());
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

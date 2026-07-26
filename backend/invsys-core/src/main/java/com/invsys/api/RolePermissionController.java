package com.invsys.api;

import com.invsys.core.security.PermissionKeys;
import com.invsys.repository.RoleRepository;
import com.invsys.service.RolePermissionService;
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

    public RolePermissionController(RolePermissionService rolePermissionService,
                                    RoleRepository roleRepository) {
        this.rolePermissionService = rolePermissionService;
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public MatrixResponse list() {
        UUID tenantId = TenantContext.requireTenantId();
        List<RoleDefinition> roles = roleRepository.findByTenantId(tenantId).stream()
                .map(r -> new RoleDefinition(r.getId(), r.getCode()))
                .toList();
        List<RolePermissionService.RolePermissionRow> rows = rolePermissionService.listForTenant();
        List<Grant> grants = rows.stream()
                .map(r -> new Grant(r.roleId(), r.permissionKey(), r.granted()))
                .toList();
        return new MatrixResponse(roles, PermissionKeys.CATALOG, grants);
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

    public record RoleDefinition(UUID id, String name) {
    }

    public record Grant(UUID roleId, String permissionKey, boolean granted) {
    }

    public record MatrixResponse(
            List<RoleDefinition> roles,
            List<String> permissionKeys,
            List<Grant> grants
    ) {
    }
}

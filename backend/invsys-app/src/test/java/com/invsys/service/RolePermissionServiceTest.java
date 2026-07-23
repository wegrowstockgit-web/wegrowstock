package com.invsys.service;

import com.invsys.domain.Role;
import com.invsys.domain.RolePermission;
import com.invsys.repository.RolePermissionRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    private static final UUID TENANT = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("e0000000-0000-4000-8000-000000000010");

    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock RoleRepository roleRepository;

    RolePermissionService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new RolePermissionService(rolePermissionRepository, roleRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void defaultOpenWhenNoRowsConfigured() {
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKey(
                TENANT, List.of(ROLE_ID), "inventory:cost:view")).thenReturn(false);

        assertThat(service.isGrantedForRoles(List.of(ROLE_ID), "inventory:cost:view")).isTrue();
    }

    @Test
    void explicitGrantAllowsAccess() {
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKey(
                TENANT, List.of(ROLE_ID), "inventory:cost:view")).thenReturn(true);
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
                TENANT, List.of(ROLE_ID), "inventory:cost:view")).thenReturn(true);

        assertThat(service.isGrantedForRoles(List.of(ROLE_ID), "inventory:cost:view")).isTrue();
    }

    @Test
    void explicitDenyBlocksAccess() {
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKey(
                TENANT, List.of(ROLE_ID), "inventory:cost:view")).thenReturn(true);
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
                TENANT, List.of(ROLE_ID), "inventory:cost:view")).thenReturn(false);

        assertThat(service.isGrantedForRoles(List.of(ROLE_ID), "inventory:cost:view")).isFalse();
    }

    @Test
    void upsertPersistsGrantFlag() {
        Role role = new Role();
        role.setId(ROLE_ID);
        role.setTenantId(TENANT);
        role.setCode("VIEWER");
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByTenantIdAndRoleIdAndPermissionKey(
                TENANT, ROLE_ID, "inventory:adjust")).thenReturn(Optional.empty());
        when(rolePermissionRepository.save(org.mockito.ArgumentMatchers.any(RolePermission.class)))
                .thenAnswer(inv -> {
                    RolePermission saved = inv.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        RolePermissionService.RolePermissionRow row = service.upsert(
                new RolePermissionService.UpsertRequest(ROLE_ID, "inventory:adjust", false));

        assertThat(row.roleId()).isEqualTo(ROLE_ID);
        assertThat(row.permissionKey()).isEqualTo("inventory:adjust");
        assertThat(row.granted()).isFalse();
        assertThat(row.roleCode()).isEqualTo("VIEWER");
    }
}

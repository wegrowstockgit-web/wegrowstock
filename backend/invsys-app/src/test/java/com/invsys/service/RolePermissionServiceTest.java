package com.invsys.service;

import com.invsys.core.common.exception.SystemRoleLockedException;
import com.invsys.domain.Role;
import com.invsys.domain.RolePermission;
import com.invsys.repository.RolePermissionRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.core.security.PermissionKeys;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    private static final UUID TENANT = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID PICKER_ID = UUID.fromString("e0000000-0000-4000-8000-000000000010");
    private static final UUID MANAGER_ID = UUID.fromString("e0000000-0000-4000-8000-000000000011");

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
    void retailRolesOnlyReceivePosPermissions() {
        assertThat(RolePermissionService.baselineGranted("RETAIL_CASHIER", PermissionKeys.POS_OPERATE)).isTrue();
        assertThat(RolePermissionService.baselineGranted("RETAIL_CASHIER", PermissionKeys.POS_SUPERVISE)).isFalse();
        assertThat(RolePermissionService.baselineGranted("RETAIL_CASHIER", PermissionKeys.INVENTORY_ADJUST)).isFalse();
        assertThat(RolePermissionService.baselineGranted("RETAIL_MANAGER", PermissionKeys.POS_OPERATE)).isTrue();
        assertThat(RolePermissionService.baselineGranted("RETAIL_MANAGER", PermissionKeys.POS_SUPERVISE)).isTrue();
        assertThat(RolePermissionService.baselineGranted("RETAIL_MANAGER", PermissionKeys.INVENTORY_ADJUST)).isFalse();
        assertThat(RolePermissionService.baselineGranted("WAREHOUSE_MANAGER", PermissionKeys.POS_SUPERVISE)).isTrue();
        assertThat(RolePermissionService.baselineGranted("PICKER", PermissionKeys.POS_OPERATE)).isFalse();
    }

    @Test
    void emptyRolesDeny() {
        assertThat(service.isGrantedForRoles(List.of(), PermissionKeys.INVENTORY_COST_VIEW)).isFalse();
    }

    @Test
    void explicitGrantAllowsAccess() {
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
                TENANT, List.of(MANAGER_ID), PermissionKeys.INVENTORY_COST_VIEW)).thenReturn(true);

        assertThat(service.isGrantedForRoles(List.of(MANAGER_ID), PermissionKeys.INVENTORY_COST_VIEW)).isTrue();
    }

    @Test
    void explicitDenyBlocksAccess() {
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
                TENANT, List.of(PICKER_ID), PermissionKeys.INVENTORY_COST_VIEW)).thenReturn(false);

        assertThat(service.isGrantedForRoles(List.of(PICKER_ID), PermissionKeys.INVENTORY_COST_VIEW)).isFalse();
    }

    @Test
    void multiRoleUnionTrueWhenAnyRoleGrants() {
        when(rolePermissionRepository.existsByTenantIdAndRoleIdInAndPermissionKeyAndGrantedTrue(
                TENANT, List.of(PICKER_ID, MANAGER_ID), PermissionKeys.INVENTORY_COST_VIEW))
                .thenReturn(true);

        assertThat(service.isGrantedForRoles(
                List.of(PICKER_ID, MANAGER_ID), PermissionKeys.INVENTORY_COST_VIEW)).isTrue();
    }

    @Test
    void resolveGrantedPermissionsUnionsAcrossRoles() {
        when(roleRepository.findByTenantIdAndCode(TENANT, "PICKER"))
                .thenReturn(Optional.of(role("PICKER", PICKER_ID)));
        when(roleRepository.findByTenantIdAndCode(TENANT, "WAREHOUSE_MANAGER"))
                .thenReturn(Optional.of(role("WAREHOUSE_MANAGER", MANAGER_ID)));

        RolePermission pickerThermal = permission(PICKER_ID, PermissionKeys.PRINTING_THERMAL, true);
        RolePermission pickerCost = permission(PICKER_ID, PermissionKeys.INVENTORY_COST_VIEW, false);
        RolePermission managerCost = permission(MANAGER_ID, PermissionKeys.INVENTORY_COST_VIEW, true);

        when(rolePermissionRepository.findByTenantIdAndRoleId(TENANT, PICKER_ID))
                .thenReturn(List.of(pickerThermal, pickerCost));
        when(rolePermissionRepository.findByTenantIdAndRoleId(TENANT, MANAGER_ID))
                .thenReturn(List.of(managerCost));

        List<String> granted = service.resolveGrantedPermissions(
                TENANT, List.of("PICKER", "WAREHOUSE_MANAGER"));

        assertThat(granted).containsExactlyInAnyOrder(
                PermissionKeys.PRINTING_THERMAL,
                PermissionKeys.INVENTORY_COST_VIEW);
    }

    @Test
    void upsertPersistsGrantFlag() {
        Role role = role("JUNIOR_BUYER", PICKER_ID);
        when(roleRepository.findById(PICKER_ID)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByTenantIdAndRoleIdAndPermissionKey(
                TENANT, PICKER_ID, "inventory:adjust")).thenReturn(Optional.empty());
        when(rolePermissionRepository.save(org.mockito.ArgumentMatchers.any(RolePermission.class)))
                .thenAnswer(inv -> {
                    RolePermission saved = inv.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        RolePermissionService.RolePermissionRow row = service.upsert(
                new RolePermissionService.UpsertRequest(PICKER_ID, "inventory:adjust", false));

        assertThat(row.roleId()).isEqualTo(PICKER_ID);
        assertThat(row.permissionKey()).isEqualTo("inventory:adjust");
        assertThat(row.granted()).isFalse();
        assertThat(row.roleCode()).isEqualTo("JUNIOR_BUYER");
    }

    @Test
    void upsertRejectsSystemRole() {
        Role role = role("PICKER", PICKER_ID);
        role.setSystemRole(true);
        when(roleRepository.findById(PICKER_ID)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.upsert(
                new RolePermissionService.UpsertRequest(PICKER_ID, "inventory:adjust", true)))
                .isInstanceOf(SystemRoleLockedException.class)
                .hasMessage(SystemRoleLockedException.DETAIL);
        verify(rolePermissionRepository, never()).save(org.mockito.ArgumentMatchers.any(RolePermission.class));
    }

    @Test
    void upsertRejectsReservedSystemCodeEvenWhenFlagUnset() {
        Role role = role("ADMIN", MANAGER_ID);
        role.setSystemRole(false);
        when(roleRepository.findById(MANAGER_ID)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.upsert(
                new RolePermissionService.UpsertRequest(MANAGER_ID, PermissionKeys.INVENTORY_COST_VIEW, false)))
                .isInstanceOf(SystemRoleLockedException.class)
                .hasMessage(SystemRoleLockedException.DETAIL);
    }

    @Test
    void deleteRejectsSystemRole() {
        Role role = role("ADMIN", MANAGER_ID);
        role.setSystemRole(true);
        when(roleRepository.findById(MANAGER_ID)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.deleteCustomRole(MANAGER_ID))
                .isInstanceOf(SystemRoleLockedException.class)
                .hasMessage(SystemRoleLockedException.DETAIL);
        verify(roleRepository, never()).delete(role);
    }

    @Test
    void replacePermissionsRejectsSystemRole() {
        Role role = role("ADMIN", MANAGER_ID);
        role.setSystemRole(true);
        when(roleRepository.findById(MANAGER_ID)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.replacePermissions(MANAGER_ID, List.of(
                new RolePermissionService.PermissionGrant(PermissionKeys.INVENTORY_COST_VIEW, true))))
                .isInstanceOf(SystemRoleLockedException.class);
    }

    @Test
    void createCustomRoleClonesGrantsAndIsNotSystem() {
        when(roleRepository.findByTenantIdAndCode(TENANT, "JUNIOR_BUYER")).thenReturn(Optional.empty());
        when(roleRepository.findById(PICKER_ID)).thenReturn(Optional.of(role("PICKER", PICKER_ID)));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role saved = inv.getArgument(0);
            saved.setId(UUID.fromString("e0000000-0000-4000-8000-000000000099"));
            return saved;
        });
        when(rolePermissionRepository.findByTenantIdAndRoleId(TENANT, PICKER_ID))
                .thenReturn(List.of(permission(PICKER_ID, PermissionKeys.PRINTING_THERMAL, true)));
        when(rolePermissionRepository.findByTenantIdAndRoleIdAndPermissionKey(any(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(rolePermissionRepository.save(any(RolePermission.class))).thenAnswer(inv -> inv.getArgument(0));

        Role created = service.createCustomRole("Junior Buyer", PICKER_ID, "Sourced from receiving");

        assertThat(created.getCode()).isEqualTo("JUNIOR_BUYER");
        assertThat(created.isSystemRole()).isFalse();
        assertThat(created.getDescription()).isEqualTo("Sourced from receiving");
        assertThat(RolePermissionService.slugifyRoleCode("Quality Control Temp"))
                .isEqualTo("QUALITY_CONTROL_TEMP");
        assertThat(Role.defaultDescription("PICKER")).isEqualTo("Pick, pack, and put-away");
        assertThat(RolePermissionService.normalizeDescription("  ")).isEqualTo(Role.CUSTOM_ROLE_FALLBACK);
    }

    @Test
    void deleteCustomRoleRemovesPermissionsAndRole() {
        Role custom = role("JUNIOR_BUYER", PICKER_ID);
        custom.setSystemRole(false);
        when(roleRepository.findById(PICKER_ID)).thenReturn(Optional.of(custom));

        service.deleteCustomRole(PICKER_ID);

        verify(rolePermissionRepository).deleteByTenantIdAndRoleId(TENANT, PICKER_ID);
        verify(roleRepository).delete(custom);
    }

    private static Role role(String code, UUID id) {
        Role role = new Role();
        role.setId(id);
        role.setTenantId(TENANT);
        role.setCode(code);
        return role;
    }

    private static RolePermission permission(UUID roleId, String key, boolean granted) {
        RolePermission row = new RolePermission();
        row.setTenantId(TENANT);
        row.setRoleId(roleId);
        row.setPermissionKey(key);
        row.setGranted(granted);
        return row;
    }
}

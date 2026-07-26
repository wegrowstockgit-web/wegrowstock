package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.PermissionKeys;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.service.RolePermissionService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5: PICKER (cost view false) + WAREHOUSE_MANAGER (cost view true) → union TRUE.
 */
@AutoConfigureMockMvc
class MultiRolePermissionUnionIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired RolePermissionService rolePermissionService;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void pickerPlusManagerUnionGrantsInventoryCostView() throws Exception {
        String slug = "rbac-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "RBAC Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Role picker = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        Role manager = roleRepository.findByTenantIdAndCode(tenantId, "WAREHOUSE_MANAGER").orElseThrow();

        assertThat(rolePermissionService.isGrantedForRoles(
                List.of(picker.getId()), PermissionKeys.INVENTORY_COST_VIEW)).isFalse();
        assertThat(rolePermissionService.isGrantedForRoles(
                List.of(manager.getId()), PermissionKeys.INVENTORY_COST_VIEW)).isTrue();
        assertThat(rolePermissionService.isGrantedForRoles(
                List.of(picker.getId(), manager.getId()), PermissionKeys.INVENTORY_COST_VIEW)).isTrue();

        User hybrid = new User();
        hybrid.setTenantId(tenantId);
        hybrid.setEmail("hybrid@" + slug + ".test");
        hybrid.setDisplayName("Hybrid Operator");
        hybrid.setPasswordHash(passwordEncoder.encode("password123"));
        hybrid.setStatus("ACTIVE");
        hybrid = userRepository.save(hybrid);
        bindRole(tenantId, hybrid.getId(), picker.getId());
        bindRole(tenantId, hybrid.getId(), manager.getId());

        List<String> granted = rolePermissionService.resolveGrantedPermissions(
                tenantId, List.of("PICKER", "WAREHOUSE_MANAGER"));
        assertThat(granted).contains(PermissionKeys.INVENTORY_COST_VIEW, PermissionKeys.PRINTING_THERMAL);

        TenantContext.setUserId(hybrid.getId());
        MeResponse me = authService.currentUser();
        assertThat(me.roles()).containsExactlyInAnyOrder("PICKER", "WAREHOUSE_MANAGER");
        assertThat(me.grantedPermissions()).contains(PermissionKeys.INVENTORY_COST_VIEW);

        mockMvc.perform(get("/api/v1/settings/permissions")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionKeys").isArray())
                .andExpect(jsonPath("$.roles").isArray());

        mockMvc.perform(patch("/api/v1/settings/permissions")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"%s","permissionKey":"so:discount:override","granted":true}
                                """.formatted(picker.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(true));
    }

    private void bindRole(UUID tenantId, UUID userId, UUID roleId) {
        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleRepository.save(ur);
    }
}

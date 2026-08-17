package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.User;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.service.UserManagementService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserMultiRoleHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserManagementService userManagementService;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void addRoleAppendsWithoutRemovingExisting() throws Exception {
        String slug = "mrole-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Multi Role Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);

        var pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        var ur = new com.invsys.domain.UserRole();
        ur.setTenantId(tenantId);
        ur.setUserId(picker.getId());
        ur.setRoleId(pickerRole.getId());
        userRoleRepository.save(ur);

        assertThat(userRoleRepository.findRoleCodesByUserId(picker.getId())).containsExactly("PICKER");

        mockMvc.perform(post("/api/v1/users/" + picker.getId() + "/roles")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"WAREHOUSE_MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles.length()").value(2));

        // Re-bind tenant after MockMvc (request filters clear TenantContext).
        TenantContext.setTenantId(tenantId);
        assertThat(userManagementService.addRole(picker.getId(), "WAREHOUSE_MANAGER"))
                .containsExactlyInAnyOrder("PICKER", "WAREHOUSE_MANAGER");
        assertThat(userRoleRepository.findRoleCodesByUserId(picker.getId()))
                .containsExactlyInAnyOrder("PICKER", "WAREHOUSE_MANAGER");

        mockMvc.perform(patch("/api/v1/users/" + picker.getId() + "/org-scope")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds":["PICKER","ADMIN","RETAIL_CASHIER"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(3))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "PICKER", "ADMIN", "RETAIL_CASHIER")));

        mockMvc.perform(put("/api/v1/users/" + picker.getId() + "/roles")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["VIEWER","PICKER"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "VIEWER", "PICKER")));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='picker@%s.test')].roles.length()".formatted(slug)).value(2));
    }

    @Test
    void inviteWithRoleIdsAssignsAllRolesOnAccept() throws Exception {
        String slug = "mrolei-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Multi Invite Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String email = "dual@" + slug + ".test";

        var created = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","roleIds":["PICKER","RETAIL_CASHIER"]}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "PICKER", "RETAIL_CASHIER")))
                .andReturn();

        mockMvc.perform(get("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roles").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "PICKER", "RETAIL_CASHIER")));

        String token = new tools.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString())
                .get("token").asString();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Dual Role","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());

        TenantContext.setTenantId(owner.tenantId());
        User dual = userRepository.findByTenantIdAndEmail(owner.tenantId(), email).orElseThrow();
        assertThat(userRoleRepository.findRoleCodesByUserId(dual.getId()))
                .containsExactlyInAnyOrder("PICKER", "RETAIL_CASHIER");
    }
}

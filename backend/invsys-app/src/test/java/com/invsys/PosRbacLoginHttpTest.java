package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PosRbacLoginHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void targetAppGatesPosAndWmsAndSyncsSupervisePinsOnly() throws Exception {
        String slug = "pos-rbac-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "POS RBAC Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        login(ownerEmail(slug), "POS").andExpect(status().isOk());
        login(ownerEmail(slug), "WMS").andExpect(status().isOk());

        TenantContext.setTenantId(owner.tenantId());
        TenantContext.setUserId(owner.userId());
        User cashier = user(owner.tenantId(), "cashier@" + slug + ".test", "Cashier", "RETAIL_CASHIER");
        User manager = user(owner.tenantId(), "mgr@" + slug + ".test", "Store Manager", "RETAIL_MANAGER");
        User picker = user(owner.tenantId(), "picker@" + slug + ".test", "Picker", "PICKER");
        authService.setTerminalPin(manager.getId(), "2468");
        authService.setTerminalPin(cashier.getId(), "1357");
        TenantContext.clear();

        login("cashier@" + slug + ".test", "POS").andExpect(status().isOk());
        login("cashier@" + slug + ".test", "WMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User does not have WMS access privileges."));

        login("picker@" + slug + ".test", "WMS").andExpect(status().isOk());
        login("picker@" + slug + ".test", "POS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User does not have POS access privileges."));

        login("mgr@" + slug + ".test", "POS").andExpect(status().isOk());
        login("mgr@" + slug + ".test", "WMS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("User does not have WMS access privileges."));

        mockMvc.perform(get("/api/v1/pos/managers/sync-pins")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(owner.tenantId().toString()))
                .andExpect(jsonPath("$.managers[?(@.managerId=='" + manager.getId() + "')]").exists())
                .andExpect(jsonPath("$.managers[?(@.managerId=='" + cashier.getId() + "')]").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String targetApp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123","targetApp":"%s"}
                        """.formatted(email, targetApp)));
    }

    private User user(UUID tenantId, String email, String name, String roleCode) {
        Role role = roleRepository.findByTenantIdAndCode(tenantId, roleCode).orElseThrow();
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setDisplayName(name);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setStatus("ACTIVE");
        user = userRepository.save(user);
        UserRole assignment = new UserRole();
        assignment.setTenantId(tenantId);
        assignment.setUserId(user.getId());
        assignment.setRoleId(role.getId());
        userRoleRepository.save(assignment);
        return user;
    }

    private static String ownerEmail(String slug) {
        return "owner@" + slug + ".test";
    }
}

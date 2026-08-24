package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.LoginRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReportsRbacHttpTest extends AbstractIntegrationTest {

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
    void financialReportsStayClosedToWarehouseManagers() throws Exception {
        String slug = "rpt-rbac-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Report RBAC Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        TenantContext.setTenantId(owner.tenantId());
        User manager = user(owner.tenantId(), "mgr@" + slug + ".test", "Manager", "WAREHOUSE_MANAGER");
        User finance = financeAdmin(owner.tenantId(), "fin@" + slug + ".test");
        TenantContext.clear();

        TokenResponse managerToken = authService.login(new LoginRequest(manager.getEmail(), "password123", "WMS"));
        TokenResponse financeToken = authService.login(new LoginRequest(finance.getEmail(), "password123", "WMS"));

        mockMvc.perform(get("/api/v1/reports/profit-margin")
                        .header("Authorization", "Bearer " + managerToken.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/fulfillment-summary")
                        .header("Authorization", "Bearer " + managerToken.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/profit-margin")
                        .header("Authorization", "Bearer " + financeToken.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/fulfillment-summary")
                        .header("Authorization", "Bearer " + financeToken.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/profit-margin")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    private User user(UUID tenantId, String email, String name, String roleCode) {
        Role role = roleRepository.findByTenantIdAndCode(tenantId, roleCode).orElseThrow();
        return assign(tenantId, email, name, role);
    }

    private User financeAdmin(UUID tenantId, String email) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCode("FINANCE_ADMIN");
        role.setSystemRole(false);
        role.setDescription("Finance administrator");
        role = roleRepository.save(role);
        return assign(tenantId, email, "Finance", role);
    }

    private User assign(UUID tenantId, String email, String name, Role role) {
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
}

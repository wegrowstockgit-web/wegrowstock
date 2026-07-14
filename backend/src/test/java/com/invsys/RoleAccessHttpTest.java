package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RoleAccessHttpTest extends AbstractIntegrationTest {

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
    void viewerCannotCreateCostCenterOrIssueRequisition() throws Exception {
        String slug = "rbac-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse ownerTokens = authService.signup(new SignupRequest(
                "RBAC Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = ownerTokens.tenantId();

        TenantContext.setTenantId(tenantId);
        Role viewerRole = roleRepository.findByTenantIdAndCode(tenantId, "VIEWER").orElseThrow();
        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();

        User viewer = new User();
        viewer.setTenantId(tenantId);
        viewer.setEmail("viewer@" + slug + ".test");
        viewer.setDisplayName("Viewer");
        viewer.setPasswordHash(passwordEncoder.encode("password123"));
        viewer.setStatus("ACTIVE");
        viewer = userRepository.save(viewer);
        UserRole vr = new UserRole();
        vr.setTenantId(tenantId);
        vr.setUserId(viewer.getId());
        vr.setRoleId(viewerRole.getId());
        userRoleRepository.save(vr);

        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);
        UserRole pr = new UserRole();
        pr.setTenantId(tenantId);
        pr.setUserId(picker.getId());
        pr.setRoleId(pickerRole.getId());
        userRoleRepository.save(pr);
        TenantContext.clear();

        TokenResponse viewerTokens = authService.login(
                new LoginRequest("viewer@" + slug + ".test", "password123"));
        TokenResponse pickerTokens = authService.login(
                new LoginRequest("picker@" + slug + ".test", "password123"));

        mockMvc.perform(post("/api/v1/cost-centers")
                        .header("Authorization", "Bearer " + viewerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/internal-requisitions")
                        .header("Authorization", "Bearer " + pickerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costCenterId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"variantId\":\""
                                + UUID.randomUUID() + "\",\"qtyRequested\":1}]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/field/van/replenish")
                        .header("Authorization", "Bearer " + pickerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromWarehouseId\":\"" + UUID.randomUUID()
                                + "\",\"toVehicleLocationId\":\"" + UUID.randomUUID()
                                + "\",\"items\":[{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/compliance/lots/by-number")
                        .param("lotNumber", "NOPE")
                        .header("Authorization", "Bearer " + viewerTokens.accessToken()))
                .andExpect(status().isNotFound());
    }
}

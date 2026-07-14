package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.WarehouseAccessFilter;
import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Location;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.domain.UserWarehouse;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.repository.UserWarehouseRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WarehouseAccessHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired UserWarehouseRepository userWarehouseRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void pickerWarehouseHeaderEnforced() throws Exception {
        String slug = "lbac-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse ownerTokens = authService.signup(new SignupRequest(
                "LBAC Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = ownerTokens.tenantId();

        TenantContext.setTenantId(tenantId);
        Location wh01 = locationRepository.findByTenantIdAndCode(tenantId, "WH-01").orElseThrow();

        Location wh02 = new Location();
        wh02.setTenantId(tenantId);
        wh02.setType("WAREHOUSE");
        wh02.setCode("WH-02");
        wh02.setName("Secondary Warehouse");
        wh02.setPath("/WH-02");
        wh02 = locationRepository.save(wh02);

        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);

        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(picker.getId());
        userRole.setRoleId(pickerRole.getId());
        userRoleRepository.save(userRole);

        UserWarehouse mapping = new UserWarehouse();
        mapping.setTenantId(tenantId);
        mapping.setUserId(picker.getId());
        mapping.setLocationId(wh01.getId());
        userWarehouseRepository.save(mapping);
        TenantContext.clear();

        TokenResponse pickerTokens = authService.login(
                new LoginRequest("picker@" + slug + ".test", "password123"));

        mockMvc.perform(get("/api/v1/locations")
                        .param("type", "WAREHOUSE")
                        .header("Authorization", "Bearer " + pickerTokens.accessToken())
                        .header(WarehouseAccessFilter.HEADER, wh02.getId().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/locations")
                        .param("type", "WAREHOUSE")
                        .header("Authorization", "Bearer " + pickerTokens.accessToken())
                        .header(WarehouseAccessFilter.HEADER, wh01.getId().toString()))
                .andExpect(status().isOk());
    }
}

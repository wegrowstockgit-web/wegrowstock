package com.invsys;

import com.invsys.auth.AuthCookieService;
import com.invsys.auth.AuthService;
import com.invsys.auth.JwtService;
import com.invsys.auth.TerminalPinBruteForceGuard;
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
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TerminalSwitchHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired LocationRepository locationRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired UserWarehouseRepository userWarehouseRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TerminalPinBruteForceGuard terminalPinBruteForceGuard;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        terminalPinBruteForceGuard.reset();
    }

    @Test
    void terminalSwitchIssuesShortLivedJwtWithoutRefreshAndLocksWarehouseClaim() throws Exception {
        String slug = "term-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse ownerTokens = authService.signup(new SignupRequest(
                "Terminal Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = ownerTokens.tenantId();

        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(ownerTokens.userId());
        Location wh01 = locationRepository.findByTenantIdAndCode(tenantId, "WH-01").orElseThrow();

        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Floor Picker");
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

        authService.setTerminalPin(picker.getId(), "4821");
        TenantContext.clear();

        TokenResponse station = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/terminal-switch")
                        .header("Authorization", "Bearer " + station.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"4821\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("TERMINAL_SWITCH"))
                .andExpect(jsonPath("$.userId").value(picker.getId().toString()))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.switchedFromUserId").value(station.userId().toString()))
                .andExpect(cookie().exists(AuthCookieService.ACCESS_COOKIE))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("refreshToken");

        String access = result.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE).getValue();
        JWTClaimsSet claims = jwtService.validateAndParse(access);
        assertThat(claims.getClaim("token_type")).isEqualTo("TERMINAL_SWITCH");
        assertThat(claims.getStringClaim("tenant_id")).isEqualTo(tenantId.toString());
        assertThat(claims.getStringClaim("bind_tenant_id")).isEqualTo(tenantId.toString());
        long ttlSeconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000L;
        assertThat(ttlSeconds).isEqualTo(300L);
        @SuppressWarnings("unchecked")
        List<String> warehouses = (List<String>) claims.getClaim("warehouse_ids");
        assertThat(warehouses).containsExactly(wh01.getId().toString());

        mockMvc.perform(post("/api/v1/auth/terminal-switch")
                        .header("Authorization", "Bearer " + station.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("INVALID_PIN"));
    }

    @Test
    void repeatedInvalidPinsLockOutStationOperator() throws Exception {
        String slug = "lock-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse ownerTokens = authService.signup(new SignupRequest(
                "Lock Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = ownerTokens.tenantId();

        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(ownerTokens.userId());
        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Floor Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);
        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(picker.getId());
        userRole.setRoleId(pickerRole.getId());
        userRoleRepository.save(userRole);
        authService.setTerminalPin(picker.getId(), "9999");
        TenantContext.clear();

        TokenResponse station = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));

        for (int i = 0; i < TerminalPinBruteForceGuard.MAX_FAILURES - 1; i++) {
            mockMvc.perform(post("/api/v1/auth/terminal-switch")
                            .header("Authorization", "Bearer " + station.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pin\":\"0000\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("INVALID_PIN"));
        }

        mockMvc.perform(post("/api/v1/auth/terminal-switch")
                        .header("Authorization", "Bearer " + station.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("PIN_LOCKED"))
                .andExpect(jsonPath("$.unlockAt").isNotEmpty());
    }

    @Test
    void inactiveOperatorPinFailsAsInvalidPinWithoutEnumeration() throws Exception {
        String slug = "inact-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse ownerTokens = authService.signup(new SignupRequest(
                "Inactive Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = ownerTokens.tenantId();

        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(ownerTokens.userId());
        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Inactive Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("INACTIVE");
        picker = userRepository.save(picker);
        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(picker.getId());
        userRole.setRoleId(pickerRole.getId());
        userRoleRepository.save(userRole);
        authService.setTerminalPin(picker.getId(), "5555");
        TenantContext.clear();

        TokenResponse station = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));
        mockMvc.perform(post("/api/v1/auth/terminal-switch")
                        .header("Authorization", "Bearer " + station.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"5555\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("INVALID_PIN"));
    }

    @Test
    void operatorCanSetOwnTerminalPin() throws Exception {
        String slug = "pin-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Pin Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(post("/api/v1/auth/terminal-pin")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1357\"}"))
                .andExpect(status().isNoContent());

        TenantContext.setTenantId(tokens.tenantId());
        User user = userRepository.findById(tokens.userId()).orElseThrow();
        assertThat(user.getTerminalPinHash()).isEqualTo(
                AuthService.hashTerminalPin(tokens.tenantId(), "1357"));
        TenantContext.clear();
    }
}

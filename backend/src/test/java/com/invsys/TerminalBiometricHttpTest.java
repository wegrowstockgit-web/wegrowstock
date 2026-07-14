package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.JwtService;
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
import com.invsys.service.TerminalBiometricService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TerminalBiometricHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired TerminalBiometricService terminalBiometricService;
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
    void terminalBiometricAssertIssuesShortLivedJwt() throws Exception {
        String slug = "bio-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse ownerTokens = authService.signup(new SignupRequest(
                "Bio Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
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

        Map<String, String> registered = terminalBiometricService.registerCredential(picker.getId(), "Glove key");
        String credentialId = registered.get("credentialId");
        String secret = registered.get("secret");
        TenantContext.clear();

        TokenResponse station = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));

        MvcResult options = mockMvc.perform(post("/api/v1/auth/terminal-biometric/options")
                        .header("Authorization", "Bearer " + station.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andReturn();

        String challenge = options.getResponse().getContentAsString()
                .replaceAll("(?s).*\"challenge\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        String signature = TerminalBiometricService.computeAssertionSignature(challenge, credentialId, secret);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/terminal-biometric")
                        .header("Authorization", "Bearer " + station.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credentialId":"%s","challenge":"%s","signature":"%s"}
                                """.formatted(credentialId, challenge, signature)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("TERMINAL_SWITCH"))
                .andExpect(jsonPath("$.userId").value(picker.getId().toString()))
                .andReturn();

        String access = result.getResponse().getContentAsString()
                .replaceAll("(?s).*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        JWTClaimsSet claims = jwtService.validateAndParse(access);
        assertThat(claims.getClaim("token_type")).isEqualTo("TERMINAL_SWITCH");
        @SuppressWarnings("unchecked")
        List<String> warehouses = (List<String>) claims.getClaim("warehouse_ids");
        assertThat(warehouses).containsExactly(wh01.getId().toString());

        mockMvc.perform(post("/api/v1/auth/terminal-biometric")
                        .header("Authorization", "Bearer " + station.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credentialId":"%s","challenge":"%s","signature":"deadbeef"}
                                """.formatted(credentialId, challenge)))
                .andExpect(status().isUnauthorized());
    }
}

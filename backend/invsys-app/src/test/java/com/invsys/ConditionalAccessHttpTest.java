package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.JwtService;
import com.invsys.core.security.NetworkAccessPolicy;
import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.NetworkAccessLevel;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.service.TerminalBiometricService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConditionalAccessHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TerminalBiometricService terminalBiometricService;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void strictExternalDenied_roamingAllowed_mfaChallengeThenVerifiedJwt() throws Exception {
        String slug = "fence-" + UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner@" + slug + ".test";
        TokenResponse owner = authService.signup(new SignupRequest(
                "Fence Co", slug, ownerEmail, "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        assertThat(pickerRole.getNetworkAccessLevel()).isEqualTo(NetworkAccessLevel.STRICT_INTERNAL);

        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);
        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setUserId(picker.getId());
        ur.setRoleId(pickerRole.getId());
        userRoleRepository.save(ur);
        TenantContext.clear();

        mockMvc.perform(patch("/api/v1/settings/permissions/allowed-cidrs")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowedCidrBlocks\":[\"10.0.0.0/8\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedCidrBlocks[0]").value("10.0.0.0/8"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"picker@" + slug + ".test\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value(NetworkAccessPolicy.STRICT_DENIED_DETAIL));

        mockMvc.perform(patch("/api/v1/settings/permissions/network-access")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + pickerRole.getId()
                                + "\",\"networkAccessLevel\":\"ROAMING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkAccessLevel").value("ROAMING"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"picker@" + slug + ".test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ownerEmail + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("MFA_REQUIRED_FOR_EXTERNAL_ACCESS"))
                .andExpect(jsonPath("$.challenge").isString());

        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(owner.userId());
        Map<String, String> cred = terminalBiometricService.registerCredential(owner.userId(), "Laptop");
        TenantContext.clear();

        MvcResult challenge = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ownerEmail + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode body = objectMapper.readTree(challenge.getResponse().getContentAsString());
        String challengeValue = body.get("challenge").asString();
        String signature = TerminalBiometricService.computeAssertionSignature(
                challengeValue, cred.get("credentialId"), cred.get("secret"));

        MvcResult loggedIn = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ownerEmail + "\",\"password\":\"password123\","
                                + "\"mfaCredentialId\":\"" + cred.get("credentialId") + "\","
                                + "\"mfaChallenge\":\"" + challengeValue + "\","
                                + "\"mfaSignature\":\"" + signature + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessCookie = loggedIn.getResponse().getCookie("invsys_access").getValue();
        assertThat(jwtService.extractMfaVerified(accessCookie)).isTrue();

        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + accessCookie)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        }))
                .andExpect(status().isOk());
    }
}

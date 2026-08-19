package com.invsys;

import com.invsys.core.security.AuthCookieService;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.ImpersonationHandoffStore;
import com.invsys.core.security.JwtService;
import com.invsys.core.security.NetworkAccessPolicy;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.NetworkAccessLevel;
import com.invsys.domain.Role;
import com.invsys.repository.RoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ImpersonationUnlockHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired ImpersonationHandoffStore impersonationHandoffStore;
    @Autowired RoleRepository roleRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        impersonationHandoffStore.resetLocal();
    }

    @Test
    void ownerLockoutFromPublicIp_impersonationHandoffRestoresMe() throws Exception {
        String slug = "lock-" + UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner@" + slug + ".test";
        TokenResponse owner = authService.signup(new SignupRequest(
                "Lockout Co", slug, ownerEmail, "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Role ownerRole = roleRepository.findByTenantIdAndCode(tenantId, "OWNER").orElseThrow();
        TenantContext.clear();

        mockMvc.perform(patch("/api/v1/settings/permissions/allowed-cidrs")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowedCidrBlocks\":[\"10.0.0.0/8\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/settings/permissions/network-access")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + ownerRole.getId()
                                + "\",\"networkAccessLevel\":\"STRICT_INTERNAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkAccessLevel").value("STRICT_INTERNAL"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ownerEmail + "\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value(NetworkAccessPolicy.STRICT_DENIED_DETAIL));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ACCESS_DENIED"));

        String impersonationJwt = jwtService.generateImpersonationAccessToken(
                owner.userId(), tenantId, java.util.List.of("OWNER"), java.util.List.of());
        String jti = jwtService.validateAndParse(impersonationJwt).getJWTID();
        String handoff = UUID.randomUUID() + UUID.randomUUID().toString();
        impersonationHandoffStore.register(
                jti, handoff, impersonationJwt, Duration.ofSeconds(JwtService.IMPERSONATION_TTL_SECONDS));

        MvcResult accept = mockMvc.perform(post("/api/v1/auth/impersonation/accept")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handoff\":\"" + handoff + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie access = accept.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        assertThat(access).isNotNull();
        assertThat(jwtService.extractSupportImpersonation(access.getValue())).isTrue();
        assertThat(jwtService.extractAppContext(access.getValue())).isEqualTo("WMS");

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(access)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ownerEmail))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }
}

package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.service.TerminalBiometricService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DesktopIdleTimeoutHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TerminalBiometricService terminalBiometricService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void settingsAndMeExposeTimeoutAndDesktopUnlockAcceptsPasswordAndPasskey() throws Exception {
        String slug = "desk-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Desktop Idle Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desktop_idle_timeout_minutes").value(30));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desktopIdleTimeoutMinutes").value(30));

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desktop_idle_timeout_minutes\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desktop_idle_timeout_minutes").value(15));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desktopIdleTimeoutMinutes").value(15));

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desktop_idle_timeout_minutes\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("DESKTOP_IDLE_TIMEOUT_UNSUPPORTED"));

        mockMvc.perform(post("/api/v1/auth/desktop-unlock")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/desktop-unlock")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());

        TenantContext.setTenantId(owner.tenantId());
        TenantContext.setUserId(owner.userId());
        Map<String, String> registered = terminalBiometricService.registerCredential(owner.userId(), "Office key");
        TenantContext.clear();

        MvcResult options = mockMvc.perform(get("/api/v1/auth/desktop-unlock/options")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPasskey").value(true))
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andReturn();
        String challenge = options.getResponse().getContentAsString()
                .replaceAll("(?s).*\"challenge\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        String credentialId = registered.get("credentialId");
        String signature = TerminalBiometricService.computeAssertionSignature(
                challenge, credentialId, registered.get("secret"));

        mockMvc.perform(post("/api/v1/auth/desktop-unlock")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mfaCredentialId":"%s","mfaChallenge":"%s","mfaSignature":"%s"}
                                """.formatted(credentialId, challenge, signature)))
                .andExpect(status().isNoContent());
    }
}

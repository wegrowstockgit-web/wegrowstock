package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthRefreshHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void refreshEndpointRotatesAccessToken() throws Exception {
        String slug = "refr-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Refresh Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        TokenResponse refreshed = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();
        // Refresh tokens are rotated; access JWT may collide within the same second.
        assertThat(refreshed.refreshToken()).isNotEqualTo(tokens.refreshToken());
    }

    @Test
    void refreshRejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void loginThenRefreshKeepsTenant() throws Exception {
        String slug = "loginr-" + UUID.randomUUID().toString().substring(0, 8);
        authService.signup(new SignupRequest(
                "Login Refresh", slug, "owner@" + slug + ".test", "password123", "Owner"));

        TokenResponse loggedIn = authService.login(new LoginRequest("owner@" + slug + ".test", "password123"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loggedIn.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();

        TokenResponse refreshed = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(refreshed.tenantId()).isEqualTo(loggedIn.tenantId());
    }
}

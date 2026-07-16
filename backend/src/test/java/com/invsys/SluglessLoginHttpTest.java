package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import com.invsys.auth.AuthCookieService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SluglessLoginHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void loginWithEmailAndPasswordSucceeds() throws Exception {
        String slug = "slugless-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Slugless Co", slug, email, "password123", "Owner"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tenantId").value(tokens.tenantId().toString()))
                .andExpect(cookie().exists(AuthCookieService.ACCESS_COOKIE))
                .andExpect(cookie().httpOnly(AuthCookieService.ACCESS_COOKIE, true));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String slug = "badpw-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        authService.signup(new SignupRequest(
                "Bad Password Co", slug, email, "password123", "Owner"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }
}

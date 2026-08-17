package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.JwtService;
import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JwtAppContextHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;

    @Test
    void posTokenCannotHitWmsRoutes_wmsTokenCannotHitPosRoutes() throws Exception {
        String slug = "appctx-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        authService.signup(new SignupRequest("AppCtx Co", slug, email, "password123", "Owner"));

        TokenResponse pos = authService.login(new LoginRequest(email, "password123", "POS"));
        TokenResponse wms = authService.login(new LoginRequest(email, "password123", "WMS"));
        TokenResponse legacy = authService.login(new LoginRequest(email, "password123"));

        assertThat(jwtService.extractAppContext(pos.accessToken())).isEqualTo("POS");
        assertThat(jwtService.extractAppContext(wms.accessToken())).isEqualTo("WMS");
        assertThat(jwtService.extractAppContext(legacy.accessToken())).isNull();

        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + pos.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/pos/session").header("Authorization", "Bearer " + pos.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + pos.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/pos/session").header("Authorization", "Bearer " + wms.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + wms.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + legacy.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pos/session").header("Authorization", "Bearer " + legacy.accessToken()))
                .andExpect(status().isOk());
    }
}

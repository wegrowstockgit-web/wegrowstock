package com.invsys.api;

import com.invsys.AbstractIntegrationTest;
import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SsoConnectionStatesHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void connectionStatesReflectConfiguredGoogleOidc() throws Exception {
        String slug = "sso-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "SSO Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(get("/api/v1/settings/sso/connection-states")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProvider").value("NONE"))
                .andExpect(jsonPath("$.providers[0].id").value("GOOGLE"))
                .andExpect(jsonPath("$.providers[0].connected").value(false));

        mockMvc.perform(put("/api/v1/settings/sso")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issuerUrl":"https://accounts.google.com",
                                  "clientId":"google-client",
                                  "clientSecret":"super-secret",
                                  "enabled":true,
                                  "forceSso":false,
                                  "protocol":"OIDC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.configured").value(true));

        mockMvc.perform(get("/api/v1/settings/sso/connection-states")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.providers[0].id").value("GOOGLE"))
                .andExpect(jsonPath("$.providers[0].connected").value(true))
                .andExpect(jsonPath("$.providers[0].status").value("CONNECTED"));
    }
}

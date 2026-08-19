package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CurrentNetworkHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerSeesPrivateAndPublicCurrentNetwork() throws Exception {
        String slug = "net-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Net Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String bearer = "Bearer " + owner.accessToken();

        mockMvc.perform(get("/api/v1/settings/network/current-ip")
                        .header("Authorization", bearer)
                        .with(request -> {
                            request.setRemoteAddr("10.8.4.2");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientIp").value("10.8.4.2"))
                .andExpect(jsonPath("$.suggestedCidr").value("10.8.4.2/32"))
                .andExpect(jsonPath("$.isPrivateNetwork").value(true))
                .andExpect(jsonPath("$.networkHint").value("Internal VPN / LAN"));

        mockMvc.perform(get("/api/v1/settings/network/current-ip")
                        .header("Authorization", bearer)
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.45");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientIp").value("198.51.100.45"))
                .andExpect(jsonPath("$.suggestedCidr").value("198.51.100.45/32"))
                .andExpect(jsonPath("$.isPrivateNetwork").value(false))
                .andExpect(jsonPath("$.networkHint").value("Public Corporate Gateway"));
    }
}

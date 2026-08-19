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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountingOAuthHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerGetsAuthUrlStatusAndTestSync() throws Exception {
        String slug = "oauth-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "OAuth Acct", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String bearer = "Bearer " + owner.accessToken();

        mockMvc.perform(get("/api/v1/integrations/QUICKBOOKS/auth-url")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value(org.hamcrest.Matchers.containsString("intuit.com")))
                .andExpect(jsonPath("$.state").isNotEmpty())
                .andExpect(jsonPath("$.provider").value("QUICKBOOKS"));

        mockMvc.perform(get("/api/v1/integrations/xero/status")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.accountName").value(""))
                .andExpect(jsonPath("$.lastSyncAt").value(""))
                .andExpect(jsonPath("$.tokenExpiringSoon").value(false));

        mockMvc.perform(post("/api/v1/integrations/QUICKBOOKS/test-sync")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.readOk").value(true))
                .andExpect(jsonPath("$.writeOk").value(true));
    }
}
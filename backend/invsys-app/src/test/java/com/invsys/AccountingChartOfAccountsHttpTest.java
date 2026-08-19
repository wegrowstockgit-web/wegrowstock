package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountingChartOfAccountsHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerFetchesSandboxAccountsAndAutoProvisions() throws Exception {
        String slug = "coa-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "CoA Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String bearer = "Bearer " + owner.accessToken();

        mockMvc.perform(get("/api/v1/integrations/accounting/accounts")
                        .param("provider", "QUICKBOOKS")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[?(@.code=='12000')]").exists())
                .andExpect(jsonPath("$[?(@.code=='50000')]").exists())
                .andExpect(jsonPath("$[?(@.code=='40000')]").exists())
                .andExpect(jsonPath("$[?(@.code=='22000')]").exists());

        mockMvc.perform(post("/api/v1/integrations/accounting/accounts/auto-provision")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"XERO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='12000')]").exists())
                .andExpect(jsonPath("$[?(@.name=='12000 - Inventory Asset')]").exists());

        mockMvc.perform(get("/api/v1/integrations/accounting/accounts")
                        .param("provider", "SAGE")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }
}

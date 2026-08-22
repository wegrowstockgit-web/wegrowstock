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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PageKnowledgeHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void preloadAndPrefixLookupReturnSeededWeGrowStockHelp() throws Exception {
        String slug = "pk-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Help Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();

        mockMvc.perform(get("/api/v1/page-knowledge/all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(70)))
                .andExpect(jsonPath("$[?(@.routePattern=='/dashboard')].title")
                        .value(org.hamcrest.Matchers.hasItem("Command Center")))
                .andExpect(jsonPath("$[?(@.routePattern=='/purchase-orders')].category")
                        .value(org.hamcrest.Matchers.hasItem("Inbound")));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/purchase-orders/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routePattern").value("/purchase-orders"))
                .andExpect(jsonPath("$.title").value("Purchase Orders"))
                .andExpect(jsonPath("$.commonMistakes[0].solution").value(org.hamcrest.Matchers.containsString("Reverse")));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/settings?tab=users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Settings — Users"));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/totally-unknown-route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/page-knowledge/all"))
                .andExpect(status().isUnauthorized());
    }
}

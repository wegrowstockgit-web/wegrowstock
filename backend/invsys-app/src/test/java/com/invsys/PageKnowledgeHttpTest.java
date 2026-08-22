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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
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
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Warehouse Managers")))
                .andExpect(jsonPath("$.keyActions[2]").value(org.hamcrest.Matchers.containsString("Mark In Transit")))
                .andExpect(jsonPath("$.keyActions[3]").value(org.hamcrest.Matchers.containsString("Mesh Network")))
                .andExpect(jsonPath("$.keyActions[4]").value(org.hamcrest.Matchers.containsString("data grid")))
                .andExpect(jsonPath("$.commonMistakes[0].solution").value(org.hamcrest.Matchers.containsString("Reverse")));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/settings?tab=users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Settings — Users"));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/purchasing/ap-ingestion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("AP Invoice Reconciliation"))
                .andExpect(jsonPath("$.summary").value(containsString("3-way match")))
                .andExpect(jsonPath("$.commonMistakes[0].mistake").value(containsString("3-Way Mismatch")));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/totally-unknown-route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/page-knowledge/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void marketRealityPlaybooksAreSeededOnOperationalRoutes() throws Exception {
        String slug = "pk-mr-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Help Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/purchasing/receive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonMistakes[*].mistake")
                        .value(hasItem(containsString("more items than we ordered"))))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("Over-Receipt Tolerances"))));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/inventory/quarantine")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonMistakes[*].mistake")
                        .value(hasItem(containsString("crushed or damaged"))))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("NEVER reverse the receipt"))));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/purchasing/suppliers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonMistakes[*].mistake")
                        .value(hasItem(containsString("3-Way Mismatch"))))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("Compare the three documents"))));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/inventory/cycle-counts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonMistakes[*].mistake")
                        .value(hasItem(containsString("edit the inventory to 0"))))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("Ghost Inventory"))));
    }

    @Test
    void enterpriseEdgeCasePlaybooksAreSeededOnReceiveAndLandedCost() throws Exception {
        String slug = "pk-ee-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Help Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/purchasing/receive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonMistakes[*].mistake")
                        .value(hasItem(containsString("Pallets"))))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("Unit of Measure"))))
                .andExpect(jsonPath("$.commonMistakes[*].mistake")
                        .value(hasItem(containsString("urgent backorder"))))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("Cross-Dock"))))
                .andExpect(jsonPath("$.proTip").value(containsString("FSMA/DSCSA")));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/purchasing/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonMistakes[*].mistake")
                        .value(hasItem(containsString("customs bill"))))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("Landed Cost Allocation"))));

        mockMvc.perform(get("/api/v1/page-knowledge")
                        .param("route", "/inventory/landed-costs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Landed Cost Allocation"))
                .andExpect(jsonPath("$.commonMistakes[*].solution")
                        .value(hasItem(containsString("Do NOT edit the PO"))));
    }
}

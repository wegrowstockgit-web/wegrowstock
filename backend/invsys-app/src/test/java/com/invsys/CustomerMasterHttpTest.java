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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CustomerMasterHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listSummaryInviteAndCreditHoldExposeB2bFields() throws Exception {
        String slug = "custm-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Customer Master Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        JsonNode tiers = objectMapper.readTree(mockMvc.perform(get("/api/v1/customers/price-tiers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String wholesaleId = null;
        for (JsonNode tier : tiers) {
            if ("Wholesale".equals(tier.get("name").asString())) {
                wholesaleId = tier.get("id").asString();
                break;
            }
        }
        if (wholesaleId == null) {
            wholesaleId = tiers.get(0).get("id").asString();
        }

        String customerId = objectMapper.readTree(mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Northwind Farms",
                                  "email":"buyer@%s.test",
                                  "paymentTerms":"NET30",
                                  "creditLimit":50000,
                                  "priceTierId":"%s",
                                  "taxExempt":true
                                }
                                """.formatted(slug, wholesaleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Northwind Farms"))
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        mockMvc.perform(get("/api/v1/customers")
                        .param("search", "Northwind")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].priceTierName").value("Wholesale"))
                .andExpect(jsonPath("$.items[0].creditLimit").value(50000))
                .andExpect(jsonPath("$.items[0].availableCredit").value(50000))
                .andExpect(jsonPath("$.items[0].portalStatus").value("NOT_INVITED"))
                .andExpect(jsonPath("$.items[0].paymentTerms").value("NET30"));

        mockMvc.perform(get("/api/v1/customers/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeB2bAccounts").value(1))
                .andExpect(jsonPath("$.totalCreditExtended").value(50000));

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/portal-invite")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portalStatus").value("PENDING"));

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit-hold")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerStatus").value("HOLD"));

        mockMvc.perform(get("/api/v1/customers/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountsOnCreditHold").value(1));
    }
}

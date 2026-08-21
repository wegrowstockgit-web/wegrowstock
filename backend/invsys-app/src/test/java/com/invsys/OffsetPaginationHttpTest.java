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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OffsetPaginationHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void purchaseOrdersAndLookupsReturnOffsetPagesWithSearch() throws Exception {
        String slug = "page-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Paging Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();

        String alphaId = createSupplier(token, "Alpha Parts");
        createSupplier(token, "Beta Supply");
        createSupplier(token, "Gamma Goods");

        createPurchaseOrder(token, alphaId, "PO-PAGE-A");
        createPurchaseOrder(token, alphaId, "PO-PAGE-B");
        createPurchaseOrder(token, alphaId, "PO-UNIQUE-ZZZ");

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "number,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items[0].number").value("PO-PAGE-A"));

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("page", "2")
                        .param("size", "2")
                        .param("sort", "number,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].number").value("PO-UNIQUE-ZZZ"));

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("search", "UNIQUE-ZZZ")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].number").value("PO-UNIQUE-ZZZ"))
                .andExpect(jsonPath("$.items[0].supplierName").value("Alpha Parts"));

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("search", "Alpha")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("search", "no-such-po")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        mockMvc.perform(get("/api/v1/suppliers")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "name,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Alpha Parts"));

        mockMvc.perform(get("/api/v1/suppliers")
                        .param("search", "Gamma")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Gamma Goods"));

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Buyer\",\"email\":\"buy@acme.test\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zeta Buyer\",\"email\":\"z@zeta.test\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customers")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "name,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Acme Buyer"));

        mockMvc.perform(get("/api/v1/customers")
                        .param("search", "Acme")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Acme Buyer"));

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .param("sort", "injected,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuRoot\":\"PAG\",\"name\":\"Paged Widget\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuRoot\":\"ZZZ\",\"name\":\"Zebra Part\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products")
                        .param("search", "Zebra")
                        .param("sort", "name,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Zebra Part"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/sales-orders")
                        .param("page", "1")
                        .param("size", "25")
                        .param("search", "none")
                        .param("status", "DRAFT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/invoices")
                        .param("page", "1")
                        .param("search", "INV")
                        .param("status", "OPEN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/manufacturing/orders")
                        .param("page", "1")
                        .param("size", "25")
                        .param("search", "MO-")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(1));
    }

    private String createSupplier(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"contact\":{}}".formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("id").asString()).isNotBlank();
        return body.get("id").asString();
    }

    private void createPurchaseOrder(String token, String supplierId, String number) throws Exception {
        mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","number":"%s","lines":[]}
                                """.formatted(supplierId, number)))
                .andExpect(status().isOk());
    }
}

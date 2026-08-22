package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.repository.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PurchaseOrderWorkspaceHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void draftLinesLockAfterSubmitAndCancelRequiresZeroReceipts() throws Exception {
        String slug = "pows-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "PO Workspace Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("POWS");
        product.setName("Workspace Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"POWS-1",
                                  "price":4.50,
                                  "currency":"USD",
                                  "weight":1,
                                  "weightUnit":"lb",
                                  "length":2,
                                  "width":2,
                                  "height":2,
                                  "dimUnit":"in"
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String supplierId = objectMapper.readTree(mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Workspace Vendor\",\"contact\":{}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"%s","number":"PO-WS-1","lines":[{"variantId":"%s","qtyOrdered":10,"unitCost":4.5}]}
                                """.formatted(supplierId, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String poId = created.get("id").asString();

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String lineId = detail.get("lines").get(0).get("id").asString();

        mockMvc.perform(patch("/api/v1/purchase-orders/" + poId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtyOrdered\":12,\"unitCost\":5.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qtyOrdered").value(12))
                .andExpect(jsonPath("$.unitCost").value(5.25));

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(patch("/api/v1/purchase-orders/" + poId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtyOrdered\":99}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}

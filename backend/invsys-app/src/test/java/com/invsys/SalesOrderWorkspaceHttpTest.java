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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SalesOrderWorkspaceHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void draftLinesLockAfterSubmitAndCancelRequiresZeroShipments() throws Exception {
        String slug = "sows-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "SO Workspace Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("SOWS");
        product.setName("Workspace Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"SOWS-1",
                                  "price":12.50,
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

        String customerId = objectMapper.readTree(mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Workspace Buyer\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","number":"SO-WS-1","lines":[{"variantId":"%s","qtyOrdered":10,"unitPrice":12.5}]}
                                """.formatted(customerId, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String orderId = created.get("id").asString();

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/sales-orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String lineId = detail.get("lines").get(0).get("id").asString();

        mockMvc.perform(patch("/api/v1/sales-orders/" + orderId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtyOrdered\":12,\"unitPrice\":13.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qtyOrdered").value(12));

        mockMvc.perform(post("/api/v1/sales-orders/" + orderId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(patch("/api/v1/sales-orders/" + orderId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtyOrdered\":99}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/sales-orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void creditHoldBlocksSubmitUntilFinanceOverridesAndLinesCanSplit() throws Exception {
        String slug = "soch-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "SO Credit Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("SOCH");
        product.setName("Hold Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"SOCH-1",
                                  "price":10,
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

        String heldCustomer = objectMapper.readTree(mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Held Buyer\",\"customerStatus\":\"HOLD\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String heldOrderId = objectMapper.readTree(mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","number":"SO-HOLD-1","lines":[{"variantId":"%s","qtyOrdered":10,"unitPrice":10}]}
                                """.formatted(heldCustomer, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        mockMvc.perform(post("/api/v1/sales-orders/" + heldOrderId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREDIT_HOLD"));

        mockMvc.perform(get("/api/v1/sales-orders/" + heldOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditStatus").value("HOLD"));

        mockMvc.perform(post("/api/v1/sales-orders/" + heldOrderId + "/override-credit-hold")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/sales-orders/" + heldOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditStatus").value("OVERRIDDEN"));

        String clearCustomer = objectMapper.readTree(mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clear Buyer\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String splitOrderId = objectMapper.readTree(mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","number":"SO-SPLIT-1","lines":[{"variantId":"%s","qtyOrdered":10,"unitPrice":10}]}
                                """.formatted(clearCustomer, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        mockMvc.perform(post("/api/v1/sales-orders/" + splitOrderId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String lineId = objectMapper.readTree(mockMvc.perform(get("/api/v1/sales-orders/" + splitOrderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("lines").get(0).get("id").asString();

        mockMvc.perform(post("/api/v1/sales-orders/" + splitOrderId + "/lines/" + lineId + "/split-backorder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtyToShipNow\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qtyBackordered").value(4));
    }
}

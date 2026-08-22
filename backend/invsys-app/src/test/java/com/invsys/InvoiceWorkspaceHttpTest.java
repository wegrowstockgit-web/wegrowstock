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
class InvoiceWorkspaceHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void issuedInvoiceCannotBeEditedAndVoidPostsCreditMemo() throws Exception {
        String slug = "invws-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Invoice Workspace Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("INVWS");
        product.setName("Invoice Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"INVWS-1",
                                  "price":20,
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
                        .content("{\"name\":\"Invoice Buyer\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String orderId = objectMapper.readTree(mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","number":"SO-INV-1","lines":[{"variantId":"%s","qtyOrdered":4,"unitPrice":20}]}
                                """.formatted(customerId, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        JsonNode invoice = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/from-sales-order/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String invoiceId = invoice.get("id").asString();

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String lineId = detail.get("lines").get(0).get("id").asString();

        mockMvc.perform(patch("/api/v1/invoices/" + invoiceId + "/lines/" + lineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qty\":99}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/void")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREDIT_MEMO"));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"));
    }

    @Test
    void partialCreditAndPaymentLeaveOriginalInvoiceInHistory() throws Exception {
        String slug = "invpc-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Invoice Partial Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("INVPC");
        product.setName("Partial Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"INVPC-1",
                                  "price":20,
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
                        .content("{\"name\":\"Partial Buyer\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String orderId = objectMapper.readTree(mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","number":"SO-PC-1","lines":[{"variantId":"%s","qtyOrdered":5,"unitPrice":20}]}
                                """.formatted(customerId, variantId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String invoiceId = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/from-sales-order/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String lineId = objectMapper.readTree(mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("lines").get(0).get("id").asString();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/credit-memo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":\"%s\",\"qty\":1}]}".formatted(lineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREDIT_MEMO"));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));
    }
}

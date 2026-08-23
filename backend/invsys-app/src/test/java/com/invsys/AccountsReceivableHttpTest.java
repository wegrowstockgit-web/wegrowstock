package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.service.InvoicingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountsReceivableHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listExposesBalanceDueOverdueAndShippedOrderSearch() throws Exception {
        String slug = "ar-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "AR Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String token = owner.accessToken();
        TenantContext.setTenantId(owner.tenantId());

        Product product = new Product();
        product.setTenantId(owner.tenantId());
        product.setSkuRoot("ARINV");
        product.setName("AR Widget");
        product = productRepository.save(product);

        String variantId = objectMapper.readTree(mockMvc.perform(post("/api/v1/variants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId":"%s",
                                  "sku":"ARINV-1",
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
                        .content("{\"name\":\"AR Buyer\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asString();

        String orderId = objectMapper.readTree(mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","number":"SO-AR-1","lines":[{"variantId":"%s","qtyOrdered":5,"unitPrice":20}]}
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

        mockMvc.perform(get("/api/v1/invoices")
                        .param("search", "AR Buyer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].total").value(100))
                .andExpect(jsonPath("$.items[0].amountPaid").value(0))
                .andExpect(jsonPath("$.items[0].balanceDue").value(100))
                .andExpect(jsonPath("$.items[0].overdue").value(false));

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));

        mockMvc.perform(get("/api/v1/invoices")
                        .param("search", "AR Buyer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].amountPaid").value(40))
                .andExpect(jsonPath("$.items[0].balanceDue").value(60));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaid").value(40))
                .andExpect(jsonPath("$.balanceDue").value(60));

        Invoice overdue = new Invoice();
        overdue.setStatus("OPEN");
        overdue.setDueAt(Instant.now().minus(2, ChronoUnit.DAYS));
        assertThat(InvoicingService.isOverdue(overdue)).isTrue();
        overdue.setStatus("PAID");
        assertThat(InvoicingService.isOverdue(overdue)).isFalse();

        mockMvc.perform(get("/api/v1/sales-orders")
                        .param("status", "SHIPPED")
                        .param("search", "SO-AR")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(post("/api/v1/documents/invoices/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceIds\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/documents/invoices/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceIds\":[\"" + invoiceId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].sent").value(false));

        mockMvc.perform(post("/api/v1/documents/invoice/email-batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceIds\":[\"" + invoiceId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1));
    }
}

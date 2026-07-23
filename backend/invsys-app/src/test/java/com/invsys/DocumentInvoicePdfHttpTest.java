package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.domain.InvoiceLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceLineRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DocumentInvoicePdfHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired CustomerRepository customerRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired InvoiceLineRepository invoiceLineRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerDownloadsInvoicePdfAndEmailsCustomer() throws Exception {
        String slug = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Docs Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        TenantContext.setTenantId(owner.tenantId());
        Customer customer = new Customer();
        customer.setTenantId(owner.tenantId());
        customer.setName("Buyer Co");
        customer.setEmail("ap@" + slug + ".test");
        customer.setBillingAddress(address("100 Buyer St", "Austin", "TX", "78701"));
        customer.setShippingAddress(address("100 Buyer St", "Austin", "TX", "78701"));
        customer = customerRepository.save(customer);

        Invoice invoice = new Invoice();
        invoice.setTenantId(owner.tenantId());
        invoice.setCustomerId(customer.getId());
        invoice.setNumber("INV-1002");
        invoice.setStatus("OPEN");
        invoice.setSubtotal(new BigDecimal("20.00"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("20.00"));
        invoice.setCurrency("USD");
        invoice.setDueAt(Instant.now().plusSeconds(86400));
        invoice = invoiceRepository.save(invoice);

        InvoiceLine line = new InvoiceLine();
        line.setTenantId(owner.tenantId());
        line.setInvoiceId(invoice.getId());
        line.setDescription("Widget A");
        line.setQty(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("10.00"));
        line.setAmount(new BigDecimal("20.00"));
        invoiceLineRepository.save(line);
        TenantContext.clear();

        MvcResult pdfResult = mockMvc.perform(get("/api/v1/documents/invoice/" + invoice.getId() + "/pdf")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("INV-1002.pdf")))
                .andReturn();

        byte[] pdf = pdfResult.getResponse().getContentAsByteArray();
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        TenantContext.setTenantId(owner.tenantId());
        Invoice archived = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(archived.getDocumentUrl()).isNotBlank().contains("/invoices/").contains(".pdf");
        TenantContext.clear();

        MvcResult emailResult = mockMvc.perform(post("/api/v1/documents/invoice/" + invoice.getId() + "/email")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(emailResult.getResponse().getContentAsString());
        assertThat(body.get("sent").asBoolean()).isTrue();
        assertThat(body.get("to").asText()).contains("ap@" + slug + ".test");
        assertThat(body.get("invoiceNumber").asText()).isEqualTo("INV-1002");
        assertThat(body.get("documentUrl").asText()).startsWith("s3://");
    }

    @Test
    void pickerForbiddenFromInvoicePdf() throws Exception {
        String slug = "dp-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Docs Deny Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult invite = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","role":"PICKER"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(invite.getResponse().getContentAsString()).get("token").asString();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Picker","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());

        var picker = authService.login(new com.invsys.core.security.dto.LoginRequest(
                "picker@" + slug + ".test", "password123"));

        mockMvc.perform(get("/api/v1/documents/invoice/" + UUID.randomUUID() + "/pdf")
                        .header("Authorization", "Bearer " + picker.accessToken()))
                .andExpect(status().isForbidden());
    }

    private static Map<String, Object> address(String line1, String city, String region, String postal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line1", line1);
        map.put("city", city);
        map.put("region", region);
        map.put("postalCode", postal);
        map.put("country", "US");
        return map;
    }
}

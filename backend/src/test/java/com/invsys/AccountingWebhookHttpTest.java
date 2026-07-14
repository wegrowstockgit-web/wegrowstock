package com.invsys;

import com.invsys.domain.Customer;
import com.invsys.domain.Invoice;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountingWebhookHttpTest extends AbstractIntegrationTest {

    private static final String SECRET = "accounting_mock_secret";

    @Autowired MockMvc mockMvc;
    @Autowired TestDataHelper testDataHelper;
    @Autowired CustomerRepository customerRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired OutboxEventRepository outboxEventRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void accountingWebhookMarksInvoicePaidIdempotently() throws Exception {
        UUID tenantId = testDataHelper.createTenant("Acct Hook", "acct-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Webhook Customer");
        customer = customerRepository.save(customer);

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomerId(customer.getId());
        invoice.setNumber("INV-ACCT-1");
        invoice.setStatus("OPEN");
        invoice.setSubtotal(new BigDecimal("150.00"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("150.00"));
        invoice.setDueAt(Instant.now().plusSeconds(86400));
        invoice = invoiceRepository.save(invoice);
        UUID invoiceId = invoice.getId();
        TenantContext.clear();

        String body = "{\"invoiceNumber\":\"INV-ACCT-1\",\"provider\":\"xero\"}";
        mockMvc.perform(post("/api/v1/public/webhooks/accounting/xero")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Accounting-Signature", hmac(body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paid"));

        TenantContext.setTenantId(tenantId);
        assertThat(invoiceRepository.findById(invoiceId).orElseThrow().getStatus()).isEqualTo("PAID");
        assertThat(outboxEventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .anyMatch(e -> "INVOICE_PAID".equals(e.getEventType()))).isTrue();

        mockMvc.perform(post("/api/v1/public/webhooks/accounting/xero")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Accounting-Signature", SECRET)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("already_paid"));
    }

    private static String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}

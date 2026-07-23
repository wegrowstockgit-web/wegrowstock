package com.invsys.documents;

import com.invsys.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRenderingServiceIT extends AbstractIntegrationTest {

    @Autowired DocumentRenderingService documentRenderingService;

    @Test
    void springThymeleafRendersInvoicePdf() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", "Acme Warehouse");
        vars.put("tenantAddressHtml", "100 Dock St<br/>Austin, TX");
        vars.put("documentTitle", "INVOICE");
        vars.put("documentNumber", "INV-1002");
        vars.put("status", "OPEN");
        vars.put("customerName", "Buyer Co");
        vars.put("customerEmail", "ap@buyer.test");
        vars.put("customerAddressHtml", "1 Market<br/>Dallas, TX");
        vars.put("invoiceDate", "2026-07-22");
        vars.put("dueDate", "2026-08-21");
        vars.put("salesOrderNumber", "SO-9");
        vars.put("lines", List.of(new DocumentLineView("Widget", "2", "$10.00", "$20.00")));
        vars.put("subtotal", "$20.00");
        vars.put("tax", "$0.00");
        vars.put("total", "$20.00");
        vars.put("currency", "USD");
        vars.put("legalDisclaimer", "Confidential.");

        byte[] pdf = documentRenderingService.generatePdf("documents/invoice_template", vars);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(500);
    }
}

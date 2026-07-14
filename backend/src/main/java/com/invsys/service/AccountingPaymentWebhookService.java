package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Invoice;
import com.invsys.integration.OutboxService;
import com.invsys.repository.InvoiceRepository;
import com.invsys.tenancy.BootstrapJdbc;
import com.invsys.tenancy.TenantContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountingPaymentWebhookService {

    private static final Set<String> PROVIDERS = Set.of("xero", "quickbooks", "qbo");
    private static final List<String> INVOICE_KEYS = List.of(
            "invoiceNumber", "invoice_number", "InvoiceNumber", "number",
            "invoiceId", "invoice_id", "InvoiceID", "Id", "id");

    private final BootstrapJdbc bootstrapJdbc;
    private final InvoiceRepository invoiceRepository;
    private final OutboxService outboxService;
    private final AccountingPaymentWebhookService self;

    public AccountingPaymentWebhookService(BootstrapJdbc bootstrapJdbc,
                                           InvoiceRepository invoiceRepository,
                                           OutboxService outboxService,
                                           @Lazy AccountingPaymentWebhookService self) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.invoiceRepository = invoiceRepository;
        this.outboxService = outboxService;
        this.self = self;
    }

    public Map<String, String> handlePayment(String provider, Map<String, Object> payload) {
        String normalized = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER",
                    "Supported providers: xero, quickbooks, qbo");
        }

        String invoiceKey = extractInvoiceKey(payload);
        if (invoiceKey == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_INVOICE",
                    "Payload must include invoice number or id");
        }

        var lookup = bootstrapJdbc.findInvoiceByNumberOrId(invoiceKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));

        TenantContext.setTenantId(lookup.tenantId());
        try {
            return self.applyPayment(lookup.id(), normalized);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public Map<String, String> applyPayment(UUID invoiceId, String provider) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));
        if ("PAID".equals(invoice.getStatus())) {
            return Map.of("status", "already_paid", "invoiceId", invoice.getId().toString());
        }
        invoice.setStatus("PAID");
        invoiceRepository.save(invoice);
        outboxService.append("INVOICE", invoice.getId(), "INVOICE_PAID", Map.of(
                "invoiceId", invoice.getId().toString(),
                "provider", provider));
        return Map.of("status", "paid", "invoiceId", invoice.getId().toString());
    }

    private static String extractInvoiceKey(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        for (String key : INVOICE_KEYS) {
            Object value = payload.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        Object invoice = payload.get("Invoice");
        if (invoice instanceof Map<?, ?> nested) {
            Object number = nested.get("InvoiceNumber");
            if (number == null) {
                number = nested.get("invoiceNumber");
            }
            if (number == null) {
                number = nested.get("Id");
            }
            if (number != null && !number.toString().isBlank()) {
                return number.toString().trim();
            }
        }
        return null;
    }
}

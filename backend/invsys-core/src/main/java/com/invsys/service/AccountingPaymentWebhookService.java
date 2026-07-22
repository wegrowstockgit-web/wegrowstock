package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.core.integration.CredentialVaultService;
import com.invsys.core.integration.OutboxService;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService credentialVaultService;
    private final String fallbackWebhookSecret;
    private final AccountingPaymentWebhookService self;

    public AccountingPaymentWebhookService(BootstrapJdbc bootstrapJdbc,
                                           InvoiceRepository invoiceRepository,
                                           OutboxService outboxService,
                                           IntegrationCredentialRepository credentialRepository,
                                           CredentialVaultService credentialVaultService,
                                           @Value("${invsys.webhooks.accounting-secret:accounting_mock_secret}")
                                           String fallbackWebhookSecret,
                                           @Lazy AccountingPaymentWebhookService self) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.invoiceRepository = invoiceRepository;
        this.outboxService = outboxService;
        this.credentialRepository = credentialRepository;
        this.credentialVaultService = credentialVaultService;
        this.fallbackWebhookSecret = fallbackWebhookSecret;
        this.self = self;
    }

    /**
     * Verifies provider signature using the tenant's encrypted credential vault
     * (system key XERO_WEBHOOK / QBO_WEBHOOK), falling back to the platform secret.
     * Routes valid payment clears through the outbox as INVOICE_PAID.
     */
    public Map<String, String> handlePayment(String provider,
                                             Map<String, Object> payload,
                                             String rawBody,
                                             String signatureHeader) {
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
            String secret = resolveWebhookSecret(lookup.tenantId(), normalized);
            if (!isValidSignature(rawBody, signatureHeader, secret)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE",
                        "Invalid accounting webhook signature");
            }
            return self.applyPayment(lookup.id(), normalized);
        } finally {
            TenantContext.clear();
        }
    }

    /** Backward-compatible entry used by tests that already signed with the platform secret. */
    public Map<String, String> handlePayment(String provider, Map<String, Object> payload) {
        return handlePayment(provider, payload, null, fallbackWebhookSecret);
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

    String resolveWebhookSecret(UUID tenantId, String provider) {
        String system = vaultSystemFor(provider);
        return credentialRepository.findByTenantIdAndSystem(tenantId, system)
                .map(IntegrationCredential::getCiphertext)
                .map(credentialVaultService::decrypt)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .filter(s -> !s.isBlank())
                .orElse(fallbackWebhookSecret);
    }

    static String vaultSystemFor(String provider) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "xero" -> "XERO_WEBHOOK";
            case "quickbooks", "qbo" -> "QBO_WEBHOOK";
            default -> "ACCOUNTING_WEBHOOK";
        };
    }

    static boolean isValidSignature(String rawBody, String signatureHeader, String secret) {
        if (secret == null || secret.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String provided = signatureHeader.trim();
        if (provided.equals(secret)) {
            return true;
        }
        if (rawBody == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.US_ASCII),
                    provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
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

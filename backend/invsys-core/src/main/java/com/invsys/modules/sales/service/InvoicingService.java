package com.invsys.modules.sales.service;

import com.invsys.core.integration.OutboxService;
import com.invsys.billing.StripeConnectGateway;
import com.invsys.core.common.ApiException;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.domain.InvoiceLine;
import com.invsys.domain.Payment;
import com.invsys.domain.PaymentIntent;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.fulfillment.domain.Shipment;
import com.invsys.modules.fulfillment.domain.ShipmentLine;
import com.invsys.domain.StripeAccount;
import com.invsys.domain.TenantSettings;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceLineRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.repository.PaymentIntentRepository;
import com.invsys.repository.PaymentRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.fulfillment.repository.ShipmentLineRepository;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.repository.StripeAccountRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.documents.DocumentArchivalService;
import com.invsys.modules.fintech.service.FintechUnderwritingService;
import com.invsys.service.CreditService;
import com.invsys.service.DocumentSequenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

@Service
public class InvoicingService {

    private static final Logger log = LoggerFactory.getLogger(InvoicingService.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final CustomerRepository customerRepository;
    private final DocumentSequenceService sequenceService;
    private final TenantSettingsRepository settingsRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentRepository paymentRepository;
    private final StripeConnectGateway stripeGateway;
    private final OutboxService outboxService;
    private final CreditService creditService;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;
    private final FintechUnderwritingService fintechUnderwritingService;
    private final ObjectProvider<DocumentArchivalService> documentArchivalService;

    public InvoicingService(InvoiceRepository invoiceRepository,
                            InvoiceLineRepository invoiceLineRepository,
                            SalesOrderRepository salesOrderRepository,
                            SalesOrderLineRepository salesOrderLineRepository,
                            CustomerRepository customerRepository,
                            DocumentSequenceService sequenceService,
                            TenantSettingsRepository settingsRepository,
                            StripeAccountRepository stripeAccountRepository,
                            PaymentIntentRepository paymentIntentRepository,
                            PaymentRepository paymentRepository,
                            StripeConnectGateway stripeGateway,
                            OutboxService outboxService,
                            CreditService creditService,
                            ShipmentRepository shipmentRepository,
                            ShipmentLineRepository shipmentLineRepository,
                            FintechUnderwritingService fintechUnderwritingService,
                            ObjectProvider<DocumentArchivalService> documentArchivalService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.customerRepository = customerRepository;
        this.sequenceService = sequenceService;
        this.settingsRepository = settingsRepository;
        this.stripeAccountRepository = stripeAccountRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentRepository = paymentRepository;
        this.stripeGateway = stripeGateway;
        this.outboxService = outboxService;
        this.creditService = creditService;
        this.shipmentRepository = shipmentRepository;
        this.shipmentLineRepository = shipmentLineRepository;
        this.fintechUnderwritingService = fintechUnderwritingService;
        this.documentArchivalService = documentArchivalService;
    }

    @Transactional
    public Invoice createFromSalesOrder(UUID salesOrderId) {
        UUID tenantId = TenantContext.requireTenantId();
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        List<Invoice> existing = invoiceRepository.findByTenantIdAndSalesOrderId(tenantId, salesOrderId);
        Map<UUID, BigDecimal> alreadyInvoiced = invoicedQtyBySalesOrderLine(existing);

        Map<UUID, BigDecimal> qtyByLine = new HashMap<>();
        if (existing.isEmpty()) {
            for (SalesOrderLine line : salesOrderLineRepository.findBySalesOrderId(order.getId())) {
                qtyByLine.put(line.getId(), line.getQtyOrdered());
            }
        } else {
            for (SalesOrderLine line : salesOrderLineRepository.findBySalesOrderId(order.getId())) {
                BigDecimal invoiced = alreadyInvoiced.getOrDefault(line.getId(), BigDecimal.ZERO);
                BigDecimal remaining = line.getQtyShipped().subtract(invoiced);
                if (remaining.signum() > 0) {
                    qtyByLine.put(line.getId(), remaining);
                }
            }
            if (qtyByLine.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_INVOICED",
                        "All shipped quantities for this sales order are already invoiced");
            }
        }

        return createInvoice(order, null, qtyByLine);
    }

    @Transactional
    public Invoice createFromShipment(UUID shipmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Shipment not found"));
        if (!shipment.getTenantId().equals(tenantId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Shipment not found");
        }

        invoiceRepository.findByTenantIdAndShipmentId(tenantId, shipmentId).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_INVOICED",
                    "Shipment already has invoice " + existing.getNumber());
        });

        SalesOrder order = salesOrderRepository.findById(shipment.getSalesOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        Map<UUID, BigDecimal> qtyByLine = new HashMap<>();
        for (ShipmentLine shipmentLine : shipmentLineRepository.findByShipmentId(shipmentId)) {
            qtyByLine.merge(shipmentLine.getSalesOrderLineId(), shipmentLine.getQuantity(), BigDecimal::add);
        }
        if (qtyByLine.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_SHIPMENT", "Shipment has no lines to invoice");
        }

        return createInvoice(order, shipmentId, qtyByLine);
    }

    private Invoice createInvoice(SalesOrder order, UUID shipmentId, Map<UUID, BigDecimal> qtyByLine) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSettings settings = settingsRepository.findByTenantId(tenantId).orElseThrow();
        String format = (String) settings.getSettings().getOrDefault("invoice_number_format", "INV-{YYYY}-{seq:5}");
        int termsDays = ((Number) settings.getSettings().getOrDefault("payment_terms_days", 30)).intValue();

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setSalesOrderId(order.getId());
        invoice.setShipmentId(shipmentId);
        invoice.setCustomerId(order.getCustomerId());
        invoice.setNumber(sequenceService.nextNumber("INVOICE", format));
        invoice.setStatus("OPEN");
        invoice.setCurrency((String) settings.getSettings().getOrDefault("currency", "USD"));
        invoice.setDueAt(Instant.now().plus(termsDays, ChronoUnit.DAYS));
        invoice = invoiceRepository.save(invoice);

        BigDecimal subtotal = BigDecimal.ZERO;
        Map<UUID, SalesOrderLine> lines = new HashMap<>();
        for (SalesOrderLine line : salesOrderLineRepository.findBySalesOrderId(order.getId())) {
            lines.put(line.getId(), line);
        }

        for (Map.Entry<UUID, BigDecimal> entry : qtyByLine.entrySet()) {
            SalesOrderLine line = lines.get(entry.getKey());
            if (line == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order line not found");
            }
            BigDecimal qty = entry.getValue();
            BigDecimal amount = line.getUnitPrice().multiply(qty);
            subtotal = subtotal.add(amount);
            InvoiceLine il = new InvoiceLine();
            il.setTenantId(tenantId);
            il.setInvoiceId(invoice.getId());
            il.setDescription("Line " + line.getId());
            il.setQty(qty);
            il.setUnitPrice(line.getUnitPrice());
            il.setAmount(amount);
            invoiceLineRepository.save(il);
        }

        invoice.setSubtotal(subtotal);
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(subtotal);
        invoice = invoiceRepository.save(invoice);
        outboxService.append("INVOICE", invoice.getId(), "INVOICE_OPEN", Map.of("invoiceId", invoice.getId()));
        archiveOpenInvoicePdf(invoice.getId());
        return invoice;
    }

    private void archiveOpenInvoicePdf(UUID invoiceId) {
        DocumentArchivalService archival = documentArchivalService.getIfAvailable();
        if (archival == null) {
            return;
        }
        try {
            archival.archiveInvoicePdf(invoiceId);
        } catch (RuntimeException ex) {
            log.warn("Invoice PDF archival deferred invoiceId={}: {}", invoiceId, ex.toString());
        }
    }

    /**
     * NONE — no invoices yet (still invoiceable from the sales order).
     * PARTIAL — invoices exist but shipped qty remains to invoice.
     * INVOICED — all shipped quantities are already covered (or fully billed on first invoice).
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> billingStatusBySalesOrderId(UUID tenantId) {
        List<Invoice> invoices = invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<UUID, List<Invoice>> byOrder = new HashMap<>();
        for (Invoice invoice : invoices) {
            if (invoice.getSalesOrderId() == null) {
                continue;
            }
            byOrder.computeIfAbsent(invoice.getSalesOrderId(), ignored -> new java.util.ArrayList<>()).add(invoice);
        }

        Map<UUID, String> result = new HashMap<>();
        for (Map.Entry<UUID, List<Invoice>> entry : byOrder.entrySet()) {
            result.put(entry.getKey(), resolveBillingStatus(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private String resolveBillingStatus(UUID salesOrderId, List<Invoice> existing) {
        if (existing == null || existing.isEmpty()) {
            return "NONE";
        }
        Map<UUID, BigDecimal> alreadyInvoiced = invoicedQtyBySalesOrderLine(existing);
        for (SalesOrderLine line : salesOrderLineRepository.findBySalesOrderId(salesOrderId)) {
            BigDecimal invoiced = alreadyInvoiced.getOrDefault(line.getId(), BigDecimal.ZERO);
            BigDecimal remaining = line.getQtyShipped().subtract(invoiced);
            if (remaining.signum() > 0) {
                return "PARTIAL";
            }
        }
        return "INVOICED";
    }

    private Map<UUID, BigDecimal> invoicedQtyBySalesOrderLine(List<Invoice> invoices) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (Invoice invoice : invoices) {
            for (InvoiceLine line : invoiceLineRepository.findByInvoiceId(invoice.getId())) {
                UUID solId = parseSalesOrderLineId(line.getDescription());
                if (solId != null) {
                    result.merge(solId, line.getQty(), BigDecimal::add);
                }
            }
        }
        return result;
    }

    private static UUID parseSalesOrderLineId(String description) {
        if (description == null || !description.startsWith("Line ")) {
            return null;
        }
        try {
            return UUID.fromString(description.substring("Line ".length()).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional
    public PaymentIntent createPaymentIntent(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));
        TenantSettings settings = settingsRepository.findByTenantId(TenantContext.requireTenantId()).orElseThrow();
        double feePercent = ((Number) settings.getSettings().getOrDefault("platform_fee_percent", 0.4)).doubleValue();
        String connectedAccount = stripeAccountRepository.findByTenantId(TenantContext.requireTenantId())
                .map(StripeAccount::getConnectedAccountId)
                .orElse("acct_mock");

        StripeConnectGateway.PaymentIntentResult result = stripeGateway.createPaymentIntent(invoice, connectedAccount, feePercent);
        PaymentIntent pi = new PaymentIntent();
        pi.setTenantId(TenantContext.requireTenantId());
        pi.setInvoiceId(invoiceId);
        pi.setExternalId(result.externalId());
        pi.setAmount(invoice.getTotal());
        pi.setCurrency(invoice.getCurrency());
        pi.setApplicationFeeAmount((BigDecimal) result.rawPayload().get("application_fee_amount"));
        pi.setConnectedAccountRef(connectedAccount);
        pi.setStatus("PENDING");
        pi.setRawPayload(result.rawPayload());
        return paymentIntentRepository.save(pi);
    }

    @Transactional
    public void settlePayment(String externalId) {
        PaymentIntent pi = paymentIntentRepository.findByProviderAndExternalId("STRIPE", externalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Payment intent not found"));
        if ("SUCCEEDED".equals(pi.getStatus())) {
            return;
        }
        pi.setStatus("SUCCEEDED");
        paymentIntentRepository.save(pi);

        Payment payment = new Payment();
        payment.setTenantId(pi.getTenantId());
        payment.setPaymentIntentId(pi.getId());
        payment.setAmount(pi.getAmount());
        payment.setFeeAmount(pi.getApplicationFeeAmount());
        payment.setBalanceTxnRef("txn_mock_" + externalId);
        paymentRepository.save(payment);

        Invoice invoice = invoiceRepository.findById(pi.getInvoiceId()).orElseThrow();
        invoice.setStatus("PAID");
        invoiceRepository.save(invoice);
        BigDecimal payback = fintechUnderwritingService.applyFactoringPayback(invoice.getId(), pi.getAmount());
        outboxService.append("INVOICE", invoice.getId(), "INVOICE_PAID", Map.of(
                "invoiceId", invoice.getId().toString(),
                "factoringPayback", payback));
        creditService.replenishCredit(invoice.getCustomerId(), pi.getAmount());
    }
}

package com.invsys.service;

import com.invsys.integration.OutboxService;
import com.invsys.billing.StripeGateway;
import com.invsys.common.ApiException;
import com.invsys.domain.Customer;
import com.invsys.domain.Invoice;
import com.invsys.domain.InvoiceLine;
import com.invsys.domain.Payment;
import com.invsys.domain.PaymentIntent;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.StripeAccount;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InvoiceLineRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.PaymentIntentRepository;
import com.invsys.repository.PaymentRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.StripeAccountRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoicingService {

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
    private final StripeGateway stripeGateway;
    private final OutboxService outboxService;
    private final CreditService creditService;

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
                            StripeGateway stripeGateway,
                            OutboxService outboxService,
                            CreditService creditService) {
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
    }

    @Transactional
    public Invoice createFromSalesOrder(UUID salesOrderId) {
        UUID tenantId = TenantContext.requireTenantId();
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        invoiceRepository.findByTenantIdAndSalesOrderId(tenantId, salesOrderId).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_INVOICED",
                    "Sales order already has invoice " + existing.getNumber());
        });

        TenantSettings settings = settingsRepository.findByTenantId(tenantId).orElseThrow();
        String format = (String) settings.getSettings().getOrDefault("invoice_number_format", "INV-{YYYY}-{seq:5}");
        int termsDays = ((Number) settings.getSettings().getOrDefault("payment_terms_days", 30)).intValue();

        Invoice invoice = new Invoice();
        invoice.setTenantId(TenantContext.requireTenantId());
        invoice.setSalesOrderId(order.getId());
        invoice.setCustomerId(order.getCustomerId());
        invoice.setNumber(sequenceService.nextNumber("INVOICE", format));
        invoice.setStatus("OPEN");
        invoice.setCurrency((String) settings.getSettings().getOrDefault("currency", "USD"));
        invoice.setDueAt(Instant.now().plus(termsDays, ChronoUnit.DAYS));

        BigDecimal subtotal = BigDecimal.ZERO;
        invoice = invoiceRepository.save(invoice);

        for (SalesOrderLine line : salesOrderLineRepository.findBySalesOrderId(order.getId())) {
            BigDecimal amount = line.getUnitPrice().multiply(line.getQtyOrdered());
            subtotal = subtotal.add(amount);
            InvoiceLine il = new InvoiceLine();
            il.setTenantId(TenantContext.requireTenantId());
            il.setInvoiceId(invoice.getId());
            il.setDescription("Line " + line.getId());
            il.setQty(line.getQtyOrdered());
            il.setUnitPrice(line.getUnitPrice());
            il.setAmount(amount);
            invoiceLineRepository.save(il);
        }
        invoice.setSubtotal(subtotal);
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(subtotal);
        invoice = invoiceRepository.save(invoice);
        outboxService.append("INVOICE", invoice.getId(), "INVOICE_OPEN", Map.of("invoiceId", invoice.getId()));
        return invoice;
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

        StripeGateway.PaymentIntentResult result = stripeGateway.createPaymentIntent(invoice, connectedAccount, feePercent);
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
        creditService.replenishCredit(invoice.getCustomerId(), pi.getAmount());
    }
}

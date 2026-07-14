package com.invsys.api;

import com.invsys.domain.Customer;
import com.invsys.domain.Invoice;
import com.invsys.domain.PaymentIntent;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.service.InvoicingService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final InvoicingService invoicingService;
    private final CustomerRepository customerRepository;

    public InvoiceController(InvoiceRepository invoiceRepository,
                             InvoicingService invoicingService,
                             CustomerRepository customerRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoicingService = invoicingService;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<InvoiceResponse> list() {
        Map<UUID, String> customerNames = customerRepository
                .findByTenantIdOrderByNameAsc(TenantContext.requireTenantId()).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
        return invoiceRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId()).stream()
                .map(invoice -> new InvoiceResponse(
                        invoice.getId(),
                        invoice.getNumber(),
                        customerNames.getOrDefault(invoice.getCustomerId(), "—"),
                        invoice.getStatus(),
                        invoice.getTotal(),
                        invoice.getCurrency(),
                        invoice.getDueAt(),
                        invoice.getSalesOrderId()))
                .toList();
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public InvoiceDetailResponse get(@PathVariable UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new com.invsys.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));
        String customerName = customerRepository.findById(invoice.getCustomerId())
                .map(Customer::getName).orElse("—");
        return new InvoiceDetailResponse(
                invoice.getId(),
                invoice.getNumber(),
                customerName,
                invoice.getStatus(),
                invoice.getTotal(),
                invoice.getCurrency(),
                invoice.getDueAt(),
                invoice.getSalesOrderId());
    }

    @PostMapping("/from-sales-order/{salesOrderId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Invoice createFromSalesOrder(@PathVariable UUID salesOrderId) {
        return invoicingService.createFromSalesOrder(salesOrderId);
    }

    @PostMapping("/{invoiceId}/payment-intent")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PaymentIntent createPaymentIntent(@PathVariable @NotNull UUID invoiceId) {
        return invoicingService.createPaymentIntent(invoiceId);
    }

    public record InvoiceDetailResponse(
            UUID id,
            String number,
            String customerName,
            String status,
            BigDecimal total,
            String currency,
            Instant dueAt,
            UUID salesOrderId
    ) {
    }

    public record InvoiceResponse(
            UUID id,
            String number,
            String customerName,
            String status,
            BigDecimal total,
            String currency,
            Instant dueAt,
            UUID salesOrderId
    ) {
    }
}

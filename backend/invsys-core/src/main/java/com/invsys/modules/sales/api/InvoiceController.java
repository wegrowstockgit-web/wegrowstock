package com.invsys.modules.sales.api;

import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.domain.PaymentIntent;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.modules.sales.service.InvoicingService;
import com.invsys.core.common.OffsetPaging;
import com.invsys.core.common.PageResponse;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> INVOICE_SORT = Set.of("createdAt", "number", "status", "total", "dueAt");

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PageResponse<InvoiceResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.requireTenantId();
        String statusFilter = status == null || status.isBlank() ? "" : status.trim();
        Page<Invoice> result = invoiceRepository.search(
                tenantId,
                OffsetPaging.keyword(search),
                statusFilter,
                OffsetPaging.of(page, size, sort, "createdAt", Sort.Direction.DESC, INVOICE_SORT));
        Set<UUID> customerIds = result.getContent().stream()
                .map(Invoice::getCustomerId)
                .collect(Collectors.toSet());
        Map<UUID, String> customerNames = customerIds.isEmpty()
                ? Map.of()
                : customerRepository.findAllById(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
        List<InvoiceResponse> items = result.getContent().stream()
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
        return PageResponse.of(result, items);
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public InvoiceDetailResponse get(@PathVariable UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new com.invsys.core.common.ApiException(
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
                invoice.getSalesOrderId(),
                invoice.getDocumentUrl());
    }

    @PostMapping("/from-sales-order/{salesOrderId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Invoice createFromSalesOrder(@PathVariable UUID salesOrderId) {
        return invoicingService.createFromSalesOrder(salesOrderId);
    }

    @PostMapping("/from-shipment/{shipmentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Invoice createFromShipment(@PathVariable UUID shipmentId) {
        return invoicingService.createFromShipment(shipmentId);
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
            UUID salesOrderId,
            String documentUrl
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

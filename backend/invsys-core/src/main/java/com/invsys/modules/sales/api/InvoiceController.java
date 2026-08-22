package com.invsys.modules.sales.api;

import com.invsys.modules.fintech.domain.FactoredInvoice;
import com.invsys.modules.fintech.repository.FactoredInvoiceRepository;
import com.invsys.modules.fintech.service.FintechUnderwritingService;
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
import com.invsys.modules.sales.domain.InvoiceLine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final FactoredInvoiceRepository factoredInvoiceRepository;
    private final FintechUnderwritingService fintechUnderwritingService;

    public InvoiceController(InvoiceRepository invoiceRepository,
                             InvoicingService invoicingService,
                             CustomerRepository customerRepository,
                             FactoredInvoiceRepository factoredInvoiceRepository,
                             FintechUnderwritingService fintechUnderwritingService) {
        this.invoiceRepository = invoiceRepository;
        this.invoicingService = invoicingService;
        this.customerRepository = customerRepository;
        this.factoredInvoiceRepository = factoredInvoiceRepository;
        this.fintechUnderwritingService = fintechUnderwritingService;
    }

    private static final Set<String> INVOICE_SORT = Set.of("createdAt", "number", "status", "total", "dueAt");

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','FINANCE_ADMIN')")
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
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','FINANCE_ADMIN')")
    public InvoiceDetailResponse get(@PathVariable UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new com.invsys.core.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));
        String customerName = customerRepository.findById(invoice.getCustomerId())
                .map(Customer::getName).orElse("—");
        List<InvoiceLineResponse> lines = invoicingService.linesFor(invoiceId).stream()
                .map(line -> new InvoiceLineResponse(
                        line.getId(),
                        line.getDescription(),
                        line.getQty(),
                        line.getUnitPrice(),
                        line.getAmount(),
                        lineKind(line.getDescription())))
                .toList();
        String factoringStatus = factoredInvoiceRepository
                .findByTenantIdAndInvoiceId(invoice.getTenantId(), invoice.getId())
                .map(FactoredInvoice::getFundingStatus)
                .orElse(null);
        return new InvoiceDetailResponse(
                invoice.getId(),
                invoice.getNumber(),
                customerName,
                invoice.getStatus(),
                invoice.getSubtotal(),
                invoice.getTax(),
                invoice.getTotal(),
                invoice.getCurrency(),
                invoice.getDueAt(),
                invoice.getSalesOrderId(),
                invoice.getDocumentUrl(),
                factoringStatus,
                lines);
    }

    @PatchMapping("/{invoiceId}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public InvoiceLine updateDraftLine(
            @PathVariable UUID invoiceId,
            @PathVariable UUID lineId,
            @RequestBody UpdateInvoiceLineRequest request) {
        return invoicingService.updateDraftLine(invoiceId, lineId, request.qty(), request.unitPrice());
    }

    @PostMapping("/{invoiceId}/issue")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public Invoice issue(@PathVariable UUID invoiceId) {
        return invoicingService.issue(invoiceId);
    }

    @PostMapping("/{invoiceId}/void")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','FINANCE_ADMIN')")
    public Invoice voidAndCreditMemo(@PathVariable UUID invoiceId) {
        return invoicingService.voidAndIssueCreditMemo(invoiceId);
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

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public Invoice recordPayment(@PathVariable UUID invoiceId, @RequestBody RecordPaymentRequest request) {
        return invoicingService.recordPayment(invoiceId, request.amount());
    }

    @PostMapping("/{invoiceId}/credit-memo")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','FINANCE_ADMIN')")
    public Invoice issuePartialCreditMemo(
            @PathVariable UUID invoiceId,
            @RequestBody PartialCreditMemoRequest request) {
        return invoicingService.issuePartialCreditMemo(invoiceId, request.lines());
    }

    @PostMapping("/{invoiceId}/factor")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public FactoredInvoice markFactored(@PathVariable UUID invoiceId) {
        return fintechUnderwritingService.requestFactoring(invoiceId);
    }

    public record InvoiceDetailResponse(
            UUID id,
            String number,
            String customerName,
            String status,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal total,
            String currency,
            Instant dueAt,
            UUID salesOrderId,
            String documentUrl,
            String factoringStatus,
            List<InvoiceLineResponse> lines
    ) {
    }

    public record RecordPaymentRequest(BigDecimal amount) {
    }

    public record PartialCreditMemoRequest(List<InvoicingService.PartialCreditLine> lines) {
    }

    public record InvoiceLineResponse(
            UUID id,
            String description,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal amount,
            String kind
    ) {
    }

    public record UpdateInvoiceLineRequest(BigDecimal qty, BigDecimal unitPrice) {
    }

    private static String lineKind(String description) {
        if (description == null) {
            return "ITEM";
        }
        String upper = description.toUpperCase();
        if (upper.contains("TAX")) {
            return "TAX";
        }
        if (upper.contains("SURCHARGE") || upper.contains("FREIGHT") || upper.contains("DUTY")) {
            return "SURCHARGE";
        }
        if (upper.startsWith("CREDIT:")) {
            return "CREDIT";
        }
        return "ITEM";
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

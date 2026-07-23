package com.invsys.documents;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.Tenant;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.domain.InvoiceLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceLineRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles invoice domain data into Thymeleaf variables (currency formatting included).
 */
@Component
public class InvoiceDocumentBuilder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final CustomerRepository customerRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final TenantRepository tenantRepository;
    private final DocumentRenderingService renderingService;

    public InvoiceDocumentBuilder(
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            CustomerRepository customerRepository,
            SalesOrderRepository salesOrderRepository,
            TenantRepository tenantRepository,
            DocumentRenderingService renderingService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.customerRepository = customerRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.tenantRepository = tenantRepository;
        this.renderingService = renderingService;
    }

    public byte[] buildPdf(UUID invoiceId) {
        return renderingService.generatePdf("documents/invoice_template", buildVariables(invoiceId));
    }

    public Map<String, Object> buildVariables(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));

        Customer customer = customerRepository.findById(invoice.getCustomerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer not found"));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tenant not found"));

        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceId(invoiceId);
        NumberFormat money = moneyFormat(invoice.getCurrency());

        List<DocumentLineView> lineViews = lines.stream()
                .map(line -> new DocumentLineView(
                        line.getDescription(),
                        formatQty(line.getQty()),
                        money.format(nz(line.getUnitPrice())),
                        money.format(nz(line.getAmount()))))
                .toList();

        String salesOrderNumber = null;
        if (invoice.getSalesOrderId() != null) {
            salesOrderNumber = salesOrderRepository.findById(invoice.getSalesOrderId())
                    .map(SalesOrder::getNumber)
                    .orElse(invoice.getSalesOrderId().toString());
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenant.getName());
        vars.put("tenantAddressHtml", escape(tenant.getName()) + "<br/>" + escape("Tenant " + tenant.getSlug()));
        vars.put("documentTitle", "INVOICE");
        vars.put("documentNumber", invoice.getNumber());
        vars.put("status", invoice.getStatus());
        vars.put("customerName", customer.getName());
        vars.put("customerEmail", customer.getEmail());
        vars.put("customerAddressHtml", DocumentAddressFormatter.toHtml(customer.getBillingAddress()));
        vars.put("invoiceDate", invoice.getCreatedAt() == null ? "—" : DAY.format(invoice.getCreatedAt()));
        vars.put("dueDate", invoice.getDueAt() == null ? "—" : DAY.format(invoice.getDueAt()));
        vars.put("salesOrderNumber", salesOrderNumber);
        vars.put("lines", lineViews);
        vars.put("subtotal", money.format(nz(invoice.getSubtotal())));
        vars.put("tax", money.format(nz(invoice.getTax())));
        vars.put("total", money.format(nz(invoice.getTotal())));
        vars.put("currency", invoice.getCurrency());
        vars.put("legalDisclaimer",
                "Payment advice reference " + invoice.getNumber()
                        + ". This invoice does not erase ledger history. © " + tenant.getName());
        return vars;
    }

    private static NumberFormat moneyFormat(String currencyCode) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        try {
            format.setCurrency(Currency.getInstance(currencyCode == null ? "USD" : currencyCode));
        } catch (IllegalArgumentException ignored) {
            format.setCurrency(Currency.getInstance("USD"));
        }
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format;
    }

    private static String formatQty(BigDecimal qty) {
        return nz(qty).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

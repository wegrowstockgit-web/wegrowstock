package com.invsys.modules.sales.api;

import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.sales.service.InvoicingService;
import com.invsys.modules.sales.service.SalesOrderService;
import com.invsys.service.SoftKitExplosionService;
import com.invsys.service.TaxService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SalesOrderController {

    private final CustomerRepository customerRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository lineRepository;
    private final SalesOrderService salesOrderService;
    private final InvoicingService invoicingService;
    private final TaxService taxService;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final SoftKitExplosionService softKitExplosionService;

    public SalesOrderController(CustomerRepository customerRepository,
                                SalesOrderRepository salesOrderRepository,
                                SalesOrderLineRepository lineRepository,
                                SalesOrderService salesOrderService,
                                InvoicingService invoicingService,
                                TaxService taxService,
                                ProductVariantRepository variantRepository,
                                ProductRepository productRepository,
                                SoftKitExplosionService softKitExplosionService) {
        this.customerRepository = customerRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.lineRepository = lineRepository;
        this.salesOrderService = salesOrderService;
        this.invoicingService = invoicingService;
        this.taxService = taxService;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.softKitExplosionService = softKitExplosionService;
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<Customer> customers() {
        return customerRepository.findByTenantIdOrderByNameAsc(TenantContext.requireTenantId());
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Customer createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setTenantId(TenantContext.requireTenantId());
        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setBillingAddress(request.billingAddress() != null ? request.billingAddress() : Map.of());
        customer.setShippingAddress(request.shippingAddress() != null ? request.shippingAddress() : Map.of());
        customer.setTaxId(request.taxId() != null ? request.taxId() : request.ein());
        if (request.paymentTerms() != null && !request.paymentTerms().isBlank()) {
            customer.setPaymentTerms(normalizePaymentTerms(request.paymentTerms()));
        }
        customer.setCreditLimit(request.creditLimit());
        String currency = request.currencyPreference() != null
                ? request.currencyPreference()
                : request.defaultCurrency();
        if (currency != null && !currency.isBlank()) {
            customer.setCurrencyPreference(currency.trim().toUpperCase());
            customer.setDefaultCurrency(currency.trim().toUpperCase());
        }
        if (request.customerStatus() != null && !request.customerStatus().isBlank()) {
            customer.setCustomerStatus(normalizeCustomerStatus(request.customerStatus()));
        }
        return customerRepository.save(customer);
    }

    private static String normalizeCustomerStatus(String raw) {
        String key = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (key) {
            case "ACTIVE" -> "ACTIVE";
            case "HOLD", "CREDIT_HOLD", "CREDITHOLD" -> "HOLD";
            case "PROSPECT" -> "PROSPECT";
            default -> throw new com.invsys.core.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "VALIDATION",
                    "customerStatus must be ACTIVE, HOLD (CreditHold), or PROSPECT");
        };
    }

    private static String normalizePaymentTerms(String raw) {
        String key = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (key) {
            case "NET30", "NET_30", "N30" -> "NET30";
            case "NET60", "NET_60", "N60" -> "NET60";
            case "DUE_ON_RECEIPT", "DUEONRECEIPT", "COD", "DUE" -> "DUE_ON_RECEIPT";
            default -> throw new com.invsys.core.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "VALIDATION",
                    "paymentTerms must be NET30, NET60, or DUE_ON_RECEIPT");
        };
    }

    @GetMapping("/sales-orders")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<SalesOrderResponse> listSalesOrders() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, String> customerNames = customerRepository
                .findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
        Map<UUID, String> billingByOrder = invoicingService.billingStatusBySalesOrderId(tenantId);
        return salesOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(order -> new SalesOrderResponse(
                        order.getId(),
                        order.getNumber(),
                        customerNames.getOrDefault(order.getCustomerId(), "—"),
                        order.getStatus(),
                        order.getChannel(),
                        order.getCreatedAt(),
                        billingByOrder.getOrDefault(order.getId(), "NONE")))
                .toList();
    }

    @PostMapping("/sales-orders")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public SalesOrder create(@Valid @RequestBody CreateSalesOrderRequest request) {
        SalesOrder order = new SalesOrder();
        order.setTenantId(TenantContext.requireTenantId());
        order.setCustomerId(request.customerId());
        order.setNumber(request.number());
        order.setChannel(request.channel() != null ? request.channel() : "DIRECT");
        order.setStatus("DRAFT");
        if (request.sourceLocationId() != null) {
            order.setSourceLocationId(request.sourceLocationId());
        }
        if (request.customerPoNumber() != null) {
            order.setCustomerPoNumber(request.customerPoNumber());
        }
        if (request.requestedShipDate() != null) {
            order.setRequestedShipDate(request.requestedShipDate());
        }
        order = salesOrderRepository.save(order);
        UUID tenantId = TenantContext.requireTenantId();
        Map<String, Object> defaultTax = taxService.defaultTaxPayload();
        for (CreateLineRequest line : request.lines()) {
            List<SoftKitExplosionService.ExplodedLine> exploded = softKitExplosionService.explode(
                    tenantId,
                    line.variantId(),
                    line.qtyOrdered(),
                    line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO,
                    true,
                    true);
            for (SoftKitExplosionService.ExplodedLine component : exploded) {
                SalesOrderLine sol = new SalesOrderLine();
                sol.setTenantId(tenantId);
                sol.setSalesOrderId(order.getId());
                sol.setVariantId(component.variantId());
                sol.setQtyOrdered(component.quantity());
                sol.setUnitPrice(component.unitPrice());
                if (!defaultTax.isEmpty()) {
                    sol.setTax(defaultTax);
                }
                lineRepository.save(sol);
            }
        }
        return order;
    }

    @GetMapping("/sales-orders/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public SalesOrderDetailResponse getSalesOrder(@PathVariable UUID id) {
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new com.invsys.core.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        String customerName = customerRepository.findById(order.getCustomerId())
                .map(Customer::getName).orElse("—");
        List<SalesOrderLineResponse> lines = lineRepository.findBySalesOrderId(id).stream()
                .map(this::toLineResponse)
                .toList();
        return new SalesOrderDetailResponse(order.getId(), order.getNumber(), customerName,
                order.getStatus(), lines);
    }

    private SalesOrderLineResponse toLineResponse(SalesOrderLine line) {
        ProductVariant variant = variantRepository.findById(line.getVariantId()).orElse(null);
        String sku = variant != null ? variant.getSku() : "—";
        String name = variant != null
                ? productRepository.findById(variant.getProductId()).map(Product::getName).orElse(sku)
                : sku;
        return new SalesOrderLineResponse(
                line.getId(), line.getVariantId(), sku, name,
                line.getQtyOrdered(), line.getQtyShipped(), line.getUnitPrice());
    }

    @PostMapping("/sales-orders/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public SalesOrder confirm(@PathVariable UUID id) {
        return salesOrderService.confirm(id);
    }

    @PostMapping("/sales-orders/{id}/allocate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public SalesOrder allocate(@PathVariable UUID id) {
        return salesOrderService.allocate(id);
    }

    @PostMapping("/sales-orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public SalesOrder cancel(@PathVariable UUID id) {
        return salesOrderService.cancel(id);
    }

    public record CreateCustomerRequest(
            @NotBlank String name,
            String email,
            Map<String, Object> billingAddress,
            Map<String, Object> shippingAddress,
            String taxId,
            String ein,
            String paymentTerms,
            java.math.BigDecimal creditLimit,
            String currencyPreference,
            String defaultCurrency,
            String customerStatus
    ) {
    }

    public record CreateSalesOrderRequest(
            @NotNull UUID customerId,
            @NotBlank String number,
            String channel,
            UUID sourceLocationId,
            String customerPoNumber,
            java.time.Instant requestedShipDate,
            List<CreateLineRequest> lines
    ) {
    }

    public record CreateLineRequest(@NotNull UUID variantId, @NotNull BigDecimal qtyOrdered, BigDecimal unitPrice) {
    }

    public record SalesOrderResponse(
            UUID id,
            String number,
            String customerName,
            String status,
            String channel,
            java.time.Instant createdAt,
            String billingStatus
    ) {
    }

    public record SalesOrderDetailResponse(
            UUID id,
            String number,
            String customerName,
            String status,
            List<SalesOrderLineResponse> lines
    ) {
    }

    public record SalesOrderLineResponse(
            UUID id,
            UUID variantId,
            String sku,
            String name,
            BigDecimal qtyOrdered,
            BigDecimal qtyShipped,
            BigDecimal unitPrice
    ) {
    }
}

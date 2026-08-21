package com.invsys.modules.sales.api;

import com.invsys.modules.sales.domain.AllocationPolicy;
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
import com.invsys.core.common.OffsetPaging;
import com.invsys.core.common.PageResponse;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private static final Set<String> CUSTOMER_SORT = Set.of("name", "createdAt", "email", "customerStatus");
    private static final Set<String> SALES_ORDER_SORT = Set.of("createdAt", "number", "status", "channel");

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public PageResponse<Customer> customers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name,asc") String sort) {
        Page<Customer> result = customerRepository.search(
                TenantContext.requireTenantId(),
                OffsetPaging.keyword(search),
                OffsetPaging.of(page, size, sort, "name", Sort.Direction.ASC, CUSTOMER_SORT));
        return PageResponse.of(result);
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
    public PageResponse<SalesOrderResponse> listSalesOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.requireTenantId();
        String statusFilter = status == null || status.isBlank() ? "" : status.trim();
        Page<SalesOrder> result = salesOrderRepository.search(
                tenantId,
                OffsetPaging.keyword(search),
                statusFilter,
                OffsetPaging.of(page, size, sort, "createdAt", Sort.Direction.DESC, SALES_ORDER_SORT));
        Set<UUID> customerIds = result.getContent().stream()
                .map(SalesOrder::getCustomerId)
                .collect(Collectors.toSet());
        Map<UUID, String> customerNames = customerIds.isEmpty()
                ? Map.of()
                : customerRepository.findAllById(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
        Set<UUID> orderIds = result.getContent().stream().map(SalesOrder::getId).collect(Collectors.toSet());
        Map<UUID, String> billingByOrder = invoicingService.billingStatusForOrders(tenantId, orderIds);
        List<SalesOrderResponse> items = result.getContent().stream()
                .map(order -> new SalesOrderResponse(
                        order.getId(),
                        order.getNumber(),
                        customerNames.getOrDefault(order.getCustomerId(), "—"),
                        order.getStatus(),
                        order.getChannel(),
                        order.getCreatedAt(),
                        billingByOrder.getOrDefault(order.getId(), "NONE"),
                        order.getAllocationPolicy() != null ? order.getAllocationPolicy().name() : AllocationPolicy.ALLOW_PARTIAL.name(),
                        order.getQuoteExpiresAt(),
                        order.getManualDiscountTotal()))
                .toList();
        return PageResponse.of(result, items);
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
        if (request.allocationPolicy() != null && !request.allocationPolicy().isBlank()) {
            try {
                order.setAllocationPolicy(AllocationPolicy.fromString(request.allocationPolicy()));
            } catch (IllegalArgumentException ex) {
                throw new com.invsys.core.common.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "VALIDATION",
                        "allocationPolicy must be SHIP_COMPLETE or ALLOW_PARTIAL");
            }
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
        return new SalesOrderDetailResponse(
                order.getId(),
                order.getNumber(),
                customerName,
                order.getStatus(),
                order.getAllocationPolicy() != null ? order.getAllocationPolicy().name() : AllocationPolicy.ALLOW_PARTIAL.name(),
                order.getQuoteExpiresAt(),
                order.getManualDiscountTotal(),
                order.getQuoteNotes(),
                lines);
    }

    private SalesOrderLineResponse toLineResponse(SalesOrderLine line) {
        ProductVariant variant = variantRepository.findById(line.getVariantId()).orElse(null);
        String sku = variant != null ? variant.getSku() : "—";
        String name = variant != null
                ? productRepository.findById(variant.getProductId()).map(Product::getName).orElse(sku)
                : sku;
        return new SalesOrderLineResponse(
                line.getId(), line.getVariantId(), sku, name,
                line.getQtyOrdered(),
                line.getQtyAllocated(),
                line.getQtyShipped(),
                line.getQtyBackordered(),
                line.getUnitPrice());
    }

    @PostMapping("/sales-orders/{id}/quote")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public SalesOrder updateQuote(@PathVariable UUID id, @Valid @RequestBody UpdateQuoteRequest request) {
        List<SalesOrderService.QuoteLinePrice> prices = request.linePrices() == null
                ? List.of()
                : request.linePrices().stream()
                        .map(p -> new SalesOrderService.QuoteLinePrice(p.lineId(), p.unitPrice()))
                        .toList();
        return salesOrderService.updateQuotePricing(
                id, prices, request.manualDiscountTotal(), request.quoteExpiresAt(), request.quoteNotes());
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
            String allocationPolicy,
            List<CreateLineRequest> lines
    ) {
    }

    public record UpdateQuoteRequest(
            List<QuoteLinePriceRequest> linePrices,
            java.math.BigDecimal manualDiscountTotal,
            java.time.Instant quoteExpiresAt,
            String quoteNotes
    ) {
    }

    public record QuoteLinePriceRequest(UUID lineId, java.math.BigDecimal unitPrice) {
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
            String billingStatus,
            String allocationPolicy,
            java.time.Instant quoteExpiresAt,
            java.math.BigDecimal manualDiscountTotal
    ) {
    }

    public record SalesOrderDetailResponse(
            UUID id,
            String number,
            String customerName,
            String status,
            String allocationPolicy,
            java.time.Instant quoteExpiresAt,
            java.math.BigDecimal manualDiscountTotal,
            String quoteNotes,
            List<SalesOrderLineResponse> lines
    ) {
    }

    public record SalesOrderLineResponse(
            UUID id,
            UUID variantId,
            String sku,
            String name,
            BigDecimal qtyOrdered,
            BigDecimal qtyAllocated,
            BigDecimal qtyShipped,
            BigDecimal qtyBackordered,
            BigDecimal unitPrice
    ) {
    }
}

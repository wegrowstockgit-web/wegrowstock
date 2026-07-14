package com.invsys.api;

import com.invsys.domain.Customer;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.service.SalesOrderService;
import com.invsys.service.TaxService;
import com.invsys.tenancy.TenantContext;
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
    private final TaxService taxService;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    public SalesOrderController(CustomerRepository customerRepository,
                                SalesOrderRepository salesOrderRepository,
                                SalesOrderLineRepository lineRepository,
                                SalesOrderService salesOrderService,
                                TaxService taxService,
                                ProductVariantRepository variantRepository,
                                ProductRepository productRepository) {
        this.customerRepository = customerRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.lineRepository = lineRepository;
        this.salesOrderService = salesOrderService;
        this.taxService = taxService;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
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
        return customerRepository.save(customer);
    }

    @GetMapping("/sales-orders")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<SalesOrderResponse> listSalesOrders() {
        Map<UUID, String> customerNames = customerRepository
                .findByTenantIdOrderByNameAsc(TenantContext.requireTenantId()).stream()
                .collect(java.util.stream.Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
        return salesOrderRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId()).stream()
                .map(order -> new SalesOrderResponse(
                        order.getId(),
                        order.getNumber(),
                        customerNames.getOrDefault(order.getCustomerId(), "—"),
                        order.getStatus(),
                        order.getChannel(),
                        order.getCreatedAt()))
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
        Map<String, Object> defaultTax = taxService.defaultTaxPayload();
        for (CreateLineRequest line : request.lines()) {
            SalesOrderLine sol = new SalesOrderLine();
            sol.setTenantId(TenantContext.requireTenantId());
            sol.setSalesOrderId(order.getId());
            sol.setVariantId(line.variantId());
            sol.setQtyOrdered(line.qtyOrdered());
            sol.setUnitPrice(line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO);
            if (!defaultTax.isEmpty()) {
                sol.setTax(defaultTax);
            }
            lineRepository.save(sol);
        }
        return order;
    }

    @GetMapping("/sales-orders/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public SalesOrderDetailResponse getSalesOrder(@PathVariable UUID id) {
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new com.invsys.common.ApiException(
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
            Map<String, Object> shippingAddress
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
            java.time.Instant createdAt
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

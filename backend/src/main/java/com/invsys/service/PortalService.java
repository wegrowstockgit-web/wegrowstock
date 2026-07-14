package com.invsys.service;

import com.invsys.api.dto.PortalCatalogItemResponse;
import com.invsys.api.dto.PortalInvoiceResponse;
import com.invsys.api.dto.PortalOrderResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.Customer;
import com.invsys.domain.CustomerCatalogRestriction;
import com.invsys.domain.CustomerPriceTier;
import com.invsys.domain.Invoice;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.VolumePriceBreak;
import com.invsys.repository.CustomerCatalogRestrictionRepository;
import com.invsys.repository.CustomerPriceTierRepository;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.repository.VolumePriceBreakRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PortalService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CustomerPriceTierRepository priceTierRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final DocumentSequenceService sequenceService;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final CreditService creditService;
    private final CustomerCatalogRestrictionRepository catalogRestrictionRepository;
    private final VolumePriceBreakRepository volumePriceBreakRepository;

    public PortalService(ProductVariantRepository variantRepository,
                         ProductRepository productRepository,
                         CustomerRepository customerRepository,
                         CustomerPriceTierRepository priceTierRepository,
                         SalesOrderRepository salesOrderRepository,
                         SalesOrderLineRepository salesOrderLineRepository,
                         InvoiceRepository invoiceRepository,
                         DocumentSequenceService sequenceService,
                         TenantSettingsRepository tenantSettingsRepository,
                         CreditService creditService,
                         CustomerCatalogRestrictionRepository catalogRestrictionRepository,
                         VolumePriceBreakRepository volumePriceBreakRepository) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.priceTierRepository = priceTierRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.invoiceRepository = invoiceRepository;
        this.sequenceService = sequenceService;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.creditService = creditService;
        this.catalogRestrictionRepository = catalogRestrictionRepository;
        this.volumePriceBreakRepository = volumePriceBreakRepository;
    }

    public List<PortalCatalogItemResponse> catalog() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        BigDecimal tierDiscount = resolveDiscount(customerId);
        CatalogFilter filter = catalogFilter(tenantId, customerId);

        List<PortalCatalogItemResponse> items = new ArrayList<>();
        for (Product product : productRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId)) {
            for (ProductVariant variant : variantRepository.findByTenantIdAndProductId(tenantId, product.getId())) {
                if (!filter.allows(product.getId(), variant.getId())) {
                    continue;
                }
                BigDecimal discount = resolveEffectiveDiscount(tenantId, variant.getId(), BigDecimal.ONE, tierDiscount);
                BigDecimal discounted = applyDiscount(variant.getPrice(), discount);
                items.add(new PortalCatalogItemResponse(
                        variant.getId(),
                        product.getId(),
                        variant.getSku(),
                        product.getName(),
                        discounted,
                        variant.getCurrency()));
            }
        }
        return items;
    }

    @Transactional
    public PortalOrderResponse createOrder(List<PortalOrderLineInput> lines) {
        return createOrder(lines, null, null);
    }

    public PortalOrderResponse createOrder(List<PortalOrderLineInput> lines,
                                           String customerPoNumber,
                                           java.time.Instant requestedShipDate) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        BigDecimal tierDiscount = resolveDiscount(customerId);
        CatalogFilter filter = catalogFilter(tenantId, customerId);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customerId);
        order.setNumber(sequenceService.nextNumber("SO", "SO-{YYYY}-{seq:5}"));
        order.setStatus("DRAFT");
        order.setChannel("PORTAL");
        if (customerPoNumber != null && !customerPoNumber.isBlank()) {
            order.setCustomerPoNumber(customerPoNumber.trim());
        }
        if (requestedShipDate != null) {
            order.setRequestedShipDate(requestedShipDate);
        }
        order = salesOrderRepository.save(order);

        BigDecimal orderTotal = BigDecimal.ZERO;
        String currency = "USD";
        for (PortalOrderLineInput line : lines) {
            ProductVariant variant = variantRepository.findById(line.variantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
            if (!filter.allows(variant.getProductId(), variant.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "CATALOG_RESTRICTED",
                        "Variant is not available in this customer catalog");
            }
            BigDecimal discount = resolveEffectiveDiscount(tenantId, variant.getId(), line.quantity(), tierDiscount);
            BigDecimal unitPrice = applyDiscount(variant.getPrice(), discount);
            currency = variant.getCurrency();
            SalesOrderLine sol = new SalesOrderLine();
            sol.setTenantId(tenantId);
            sol.setSalesOrderId(order.getId());
            sol.setVariantId(variant.getId());
            sol.setQtyOrdered(line.quantity());
            sol.setUnitPrice(unitPrice);
            salesOrderLineRepository.save(sol);
            orderTotal = orderTotal.add(unitPrice.multiply(line.quantity()));
        }

        if (paymentTerms().startsWith("NET")) {
            creditService.reserveCredit(customerId, orderTotal);
        }

        return toOrderResponse(order, orderTotal, currency);
    }

    public List<PortalOrderResponse> orders() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        return salesOrderRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId).stream()
                .map(o -> toOrderResponse(o, orderTotal(o.getId()), orderCurrency(o.getId())))
                .toList();
    }

    public CreditSummary creditSummary() {
        UUID customerId = TenantContext.requireCustomerId();
        var line = creditService.getOrDefault(customerId);
        return new CreditSummary(line.getCreditLimit(), line.getAvailableCredit(), line.getStatus());
    }

    private PortalOrderResponse toOrderResponse(SalesOrder order, BigDecimal total, String currency) {
        return new PortalOrderResponse(order.getId(), order.getNumber(), order.getStatus(), total, currency, order.getCreatedAt());
    }

    private BigDecimal orderTotal(UUID orderId) {
        return salesOrderLineRepository.findBySalesOrderId(orderId).stream()
                .map(line -> line.getUnitPrice().multiply(line.getQtyOrdered()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String orderCurrency(UUID orderId) {
        return salesOrderLineRepository.findBySalesOrderId(orderId).stream()
                .findFirst()
                .flatMap(line -> variantRepository.findById(line.getVariantId()).map(ProductVariant::getCurrency))
                .orElse("USD");
    }

    public List<PortalInvoiceResponse> invoices() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        return invoiceRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId).stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    public List<PortalReorderLineResponse> reorderLines(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));
        if (!customerId.equals(invoice.getCustomerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Invoice not accessible");
        }
        if (invoice.getSalesOrderId() == null) {
            return List.of();
        }
        return linesForSalesOrder(invoice.getSalesOrderId());
    }

    public List<PortalReorderLineResponse> reorderLinesFromOrder(UUID orderId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found"));
        if (!customerId.equals(order.getCustomerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Order not accessible");
        }
        return linesForSalesOrder(orderId);
    }

    private List<PortalReorderLineResponse> linesForSalesOrder(UUID salesOrderId) {
        return salesOrderLineRepository.findBySalesOrderId(salesOrderId).stream()
                .map(line -> {
                    ProductVariant variant = variantRepository.findById(line.getVariantId()).orElse(null);
                    String sku = variant != null ? variant.getSku() : "—";
                    String name = variant != null
                            ? productRepository.findById(variant.getProductId()).map(Product::getName).orElse(sku)
                            : sku;
                    return new PortalReorderLineResponse(line.getVariantId(), sku, name, line.getQtyOrdered());
                })
                .toList();
    }

    public String paymentTerms() {
        TenantContext.requireTenantId();
        int days = tenantSettingsRepository.findAll().stream()
                .findFirst()
                .map(s -> s.getSettings().get("payment_terms_days"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(30);
        return "NET " + days;
    }

    private PortalInvoiceResponse toInvoiceResponse(Invoice invoice) {
        return new PortalInvoiceResponse(
                invoice.getId(),
                invoice.getNumber(),
                invoice.getStatus(),
                invoice.getTotal(),
                invoice.getCurrency(),
                invoice.getDueAt());
    }

    private CatalogFilter catalogFilter(UUID tenantId, UUID customerId) {
        List<CustomerCatalogRestriction> restrictions =
                catalogRestrictionRepository.findByTenantIdAndCustomerId(tenantId, customerId);
        if (restrictions.isEmpty()) {
            return CatalogFilter.unrestricted();
        }
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> variantIds = new HashSet<>();
        for (CustomerCatalogRestriction restriction : restrictions) {
            if ("PRODUCT".equalsIgnoreCase(restriction.getTargetType())) {
                productIds.add(restriction.getTargetId());
            } else if ("VARIANT".equalsIgnoreCase(restriction.getTargetType())) {
                variantIds.add(restriction.getTargetId());
            }
        }
        return new CatalogFilter(true, productIds, variantIds);
    }

    private BigDecimal resolveEffectiveDiscount(UUID tenantId, UUID variantId, BigDecimal quantity,
                                                BigDecimal tierDiscount) {
        return volumePriceBreakRepository
                .findFirstByTenantIdAndVariantIdAndMinQuantityLessThanEqualOrderByMinQuantityDesc(
                        tenantId, variantId, quantity)
                .map(VolumePriceBreak::getDiscountPercent)
                .orElse(tierDiscount);
    }

    private BigDecimal resolveDiscount(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer not found"));
        if (customer.getPriceTierId() == null) {
            return BigDecimal.ZERO;
        }
        return priceTierRepository.findById(customer.getPriceTierId())
                .map(CustomerPriceTier::getDiscountPercent)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        BigDecimal factor = BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return price.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }

    private record CatalogFilter(boolean restricted, Set<UUID> productIds, Set<UUID> variantIds) {
        static CatalogFilter unrestricted() {
            return new CatalogFilter(false, Set.of(), Set.of());
        }

        boolean allows(UUID productId, UUID variantId) {
            if (!restricted) {
                return true;
            }
            return variantIds.contains(variantId) || productIds.contains(productId);
        }
    }

    public record PortalOrderLineInput(UUID variantId, BigDecimal quantity) {
    }

    public record PortalReorderLineResponse(UUID variantId, String sku, String name, BigDecimal quantity) {
    }

    public record CreditSummary(BigDecimal creditLimit, BigDecimal availableCredit, String status) {
    }
}

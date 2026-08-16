package com.invsys.service;

import com.invsys.api.dto.PortalCatalogItemResponse;
import com.invsys.api.dto.PortalInvoiceResponse;
import com.invsys.api.dto.PortalOrderResponse;
import com.invsys.core.common.ApiException;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.domain.CustomerCatalogRestriction;
import com.invsys.domain.CustomerPriceTier;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.domain.ProductMedia;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.AllocationPolicy;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.domain.SalesOrderStatus;
import com.invsys.modules.sales.service.SalesOrderService;
import com.invsys.domain.VolumePriceBreak;
import com.invsys.modules.sales.repository.CustomerCatalogRestrictionRepository;
import com.invsys.modules.sales.repository.CustomerPriceTierRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.modules.catalog.repository.ProductMediaRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.repository.VolumePriceBreakRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ProductMediaRepository productMediaRepository;
    private final SoftKitExplosionService softKitExplosionService;
    private final SalesOrderService salesOrderService;

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
                         VolumePriceBreakRepository volumePriceBreakRepository,
                         ProductMediaRepository productMediaRepository,
                         SoftKitExplosionService softKitExplosionService,
                         SalesOrderService salesOrderService) {
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
        this.productMediaRepository = productMediaRepository;
        this.softKitExplosionService = softKitExplosionService;
        this.salesOrderService = salesOrderService;
    }

    public List<PortalCatalogItemResponse> catalog() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        BigDecimal tierDiscount = resolveDiscount(customerId);
        CatalogFilter filter = catalogFilter(tenantId, customerId);

        List<ProductVariant> allowedVariants = new ArrayList<>();
        for (Product product : productRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId)) {
            for (ProductVariant variant : variantRepository.findByTenantIdAndProductId(tenantId, product.getId())) {
                if (filter.allows(product.getId(), variant.getId())) {
                    allowedVariants.add(variant);
                }
            }
        }
        Map<UUID, String> primaryMedia = productMediaRepository
                .findByTenantIdAndVariantIdInAndPrimaryTrue(
                        tenantId, allowedVariants.stream().map(ProductVariant::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductMedia::getVariantId, ProductMedia::getUrl, (a, b) -> a));
        Map<UUID, String> productNames = productRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId)
                .stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));

        List<PortalCatalogItemResponse> items = new ArrayList<>();
        for (ProductVariant variant : allowedVariants) {
            BigDecimal discount = resolveEffectiveDiscount(tenantId, variant.getId(), BigDecimal.ONE, tierDiscount);
            BigDecimal discounted = applyDiscount(variant.getPrice(), discount);
            items.add(new PortalCatalogItemResponse(
                    variant.getId(),
                    variant.getProductId(),
                    variant.getSku(),
                    productNames.getOrDefault(variant.getProductId(), variant.getSku()),
                    discounted,
                    variant.getCurrency(),
                    primaryMedia.get(variant.getId())));
        }
        return items;
    }

    /**
     * Guest catalog: list/MSRP prices only — never customer-tier wholesale rates.
     */
    public List<PortalCatalogItemResponse> publicCatalog(UUID tenantId) {
        List<ProductVariant> variants = new ArrayList<>();
        Map<UUID, String> productNames = new java.util.LinkedHashMap<>();
        for (Product product : productRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId)) {
            productNames.put(product.getId(), product.getName());
            variants.addAll(variantRepository.findByTenantIdAndProductId(tenantId, product.getId()));
        }
        Map<UUID, String> primaryMedia = productMediaRepository
                .findByTenantIdAndVariantIdInAndPrimaryTrue(
                        tenantId, variants.stream().map(ProductVariant::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductMedia::getVariantId, ProductMedia::getUrl, (a, b) -> a));
        List<PortalCatalogItemResponse> items = new ArrayList<>();
        for (ProductVariant variant : variants) {
            items.add(new PortalCatalogItemResponse(
                    variant.getId(),
                    variant.getProductId(),
                    variant.getSku(),
                    productNames.getOrDefault(variant.getProductId(), variant.getSku()),
                    variant.getPrice(),
                    variant.getCurrency(),
                    primaryMedia.get(variant.getId())));
        }
        return items;
    }

    @Transactional
    public PortalOrderResponse createOrder(List<PortalOrderLineInput> lines) {
        return createOrder(lines, null, null, AllocationPolicy.ALLOW_PARTIAL);
    }

    @Transactional
    public PortalOrderResponse createOrder(List<PortalOrderLineInput> lines,
                                           String customerPoNumber,
                                           java.time.Instant requestedShipDate) {
        return createOrder(lines, customerPoNumber, requestedShipDate, AllocationPolicy.ALLOW_PARTIAL);
    }

    @Transactional
    public PortalOrderResponse createOrder(List<PortalOrderLineInput> lines,
                                           String customerPoNumber,
                                           java.time.Instant requestedShipDate,
                                           AllocationPolicy allocationPolicy) {
        return persistPortalOrder(lines, customerPoNumber, requestedShipDate, allocationPolicy,
                SalesOrderStatus.DRAFT.name(), null, true);
    }

    @Transactional
    public PortalOrderResponse requestQuote(List<PortalOrderLineInput> lines,
                                            String customerPoNumber,
                                            java.time.Instant requestedShipDate,
                                            AllocationPolicy allocationPolicy,
                                            String quoteNotes) {
        return persistPortalOrder(lines, customerPoNumber, requestedShipDate,
                allocationPolicy != null ? allocationPolicy : AllocationPolicy.ALLOW_PARTIAL,
                SalesOrderStatus.PENDING_REP_APPROVAL.name(), quoteNotes, false);
    }

    @Transactional
    public PortalOrderResponse acceptQuote(UUID orderId) {
        UUID customerId = TenantContext.requireCustomerId();
        SalesOrder order = ownedOrder(orderId, customerId);
        if (!SalesOrderStatus.QUOTE_READY.name().equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Quote is not ready to accept");
        }
        if (order.getQuoteExpiresAt() != null && !order.getQuoteExpiresAt().isAfter(java.time.Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTE_EXPIRED", "This quote has expired");
        }
        if (paymentTerms().startsWith("NET")) {
            creditService.reserveCredit(customerId, orderTotal(order.getId()));
        }
        SalesOrder accepted = salesOrderService.acceptQuote(order.getId());
        return toOrderResponse(accepted, orderTotal(accepted.getId()), orderCurrency(accepted.getId()));
    }

    private PortalOrderResponse persistPortalOrder(List<PortalOrderLineInput> lines,
                                                   String customerPoNumber,
                                                   java.time.Instant requestedShipDate,
                                                   AllocationPolicy allocationPolicy,
                                                   String status,
                                                   String quoteNotes,
                                                   boolean reserveCredit) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        BigDecimal tierDiscount = resolveDiscount(customerId);
        CatalogFilter filter = catalogFilter(tenantId, customerId);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customerId);
        order.setNumber(sequenceService.nextNumber("SO", "SO-{YYYY}-{seq:5}"));
        order.setStatus(status);
        order.setChannel("PORTAL");
        order.setAllocationPolicy(allocationPolicy != null ? allocationPolicy : AllocationPolicy.ALLOW_PARTIAL);
        if (quoteNotes != null && !quoteNotes.isBlank()) {
            order.setQuoteNotes(quoteNotes.trim());
        }
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
            List<SoftKitExplosionService.ExplodedLine> exploded = softKitExplosionService.explode(
                    tenantId, variant.getId(), line.quantity(), unitPrice, true, true);
            for (SoftKitExplosionService.ExplodedLine component : exploded) {
                SalesOrderLine sol = new SalesOrderLine();
                sol.setTenantId(tenantId);
                sol.setSalesOrderId(order.getId());
                sol.setVariantId(component.variantId());
                sol.setQtyOrdered(component.quantity());
                sol.setUnitPrice(component.unitPrice());
                salesOrderLineRepository.save(sol);
            }
            // Kit sell price stays on the parent quantity × unit price (attached to first component).
            orderTotal = orderTotal.add(unitPrice.multiply(line.quantity()));
        }

        if (reserveCredit && paymentTerms().startsWith("NET")) {
            creditService.reserveCredit(customerId, orderTotal);
        }

        return toOrderResponse(order, orderTotal, currency);
    }

    private SalesOrder ownedOrder(UUID orderId, UUID customerId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found"));
        if (!customerId.equals(order.getCustomerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Order not accessible");
        }
        return order;
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
        BigDecimal discount = order.getManualDiscountTotal() != null ? order.getManualDiscountTotal() : BigDecimal.ZERO;
        BigDecimal net = total.subtract(discount);
        if (net.signum() < 0) {
            net = BigDecimal.ZERO;
        }
        return new PortalOrderResponse(
                order.getId(),
                order.getNumber(),
                order.getStatus(),
                net,
                currency,
                order.getCreatedAt(),
                order.getAllocationPolicy() != null ? order.getAllocationPolicy().name() : AllocationPolicy.ALLOW_PARTIAL.name(),
                order.getQuoteExpiresAt(),
                discount,
                order.getQuoteNotes());
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

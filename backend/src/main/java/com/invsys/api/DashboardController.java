package com.invsys.api;

import com.invsys.api.dto.DashboardStatsResponse;
import com.invsys.domain.Customer;
import com.invsys.domain.DemandForecast;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
public class DashboardController {

    private final InventoryLevelRepository levelRepository;
    private final ProductVariantRepository variantRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final DemandForecastRepository forecastRepository;

    public DashboardController(InventoryLevelRepository levelRepository,
                               ProductVariantRepository variantRepository,
                               SalesOrderRepository salesOrderRepository,
                               InvoiceRepository invoiceRepository,
                               TenantSettingsRepository tenantSettingsRepository,
                               CustomerRepository customerRepository,
                               ProductRepository productRepository,
                               DemandForecastRepository forecastRepository) {
        this.levelRepository = levelRepository;
        this.variantRepository = variantRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.invoiceRepository = invoiceRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.forecastRepository = forecastRepository;
    }

    @GetMapping("/stats")
    public DashboardStatsResponse stats() {
        TenantContext.requireTenantId();

        List<String> openStatuses = List.of("CONFIRMED", "ALLOCATED", "PARTIALLY_SHIPPED");
        List<String> unpaidStatuses = List.of("OPEN", "PARTIALLY_PAID");

        long openOrdersCount = salesOrderRepository.findAll().stream()
                .filter(order -> openStatuses.contains(order.getStatus()))
                .count();
        long unpaidInvoicesCount = invoiceRepository.findAll().stream()
                .filter(invoice -> unpaidStatuses.contains(invoice.getStatus()))
                .count();

        List<InventoryLevel> levels = levelRepository.findAll();
        List<ProductVariant> variants = variantRepository.findAll();

        Map<UUID, BigDecimal> onHandByVariant = levels.stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getOnHand,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        Map<UUID, BigDecimal> availableByVariant = levels.stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAvailable,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        BigDecimal stockValue = variants.stream()
                .map(variant -> onHandByVariant
                        .getOrDefault(variant.getId(), BigDecimal.ZERO)
                        .multiply(variant.getPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long lowStockCount = variants.stream()
                .filter(variant -> availableByVariant
                        .getOrDefault(variant.getId(), BigDecimal.ZERO)
                        .compareTo(variant.getReorderPoint()) < 0)
                .count();

        String currency = tenantSettingsRepository.findAll().stream()
                .findFirst()
                .map(settings -> settings.getSettings().get("currency"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse("USD");

        return new DashboardStatsResponse(
                stockValue,
                currency,
                lowStockCount,
                openOrdersCount,
                unpaidInvoicesCount
        );
    }

    @GetMapping("/recent-orders")
    public List<RecentOrderResponse> recentOrders() {
        TenantContext.requireTenantId();

        Map<UUID, String> customerNames = customerRepository.findAll().stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));

        return salesOrderRepository.findAll().stream()
                .sorted(Comparator.comparing(SalesOrder::getCreatedAt).reversed())
                .limit(6)
                .map(order -> new RecentOrderResponse(
                        order.getId(),
                        order.getNumber(),
                        customerNames.getOrDefault(order.getCustomerId(), "—"),
                        order.getStatus(),
                        order.getCreatedAt()))
                .toList();
    }

    @GetMapping("/low-stock")
    public List<LowStockItemResponse> lowStock() {
        TenantContext.requireTenantId();

        Map<UUID, BigDecimal> availableByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAvailable,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        Map<UUID, String> productNames = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));

        return variantRepository.findAll().stream()
                .map(variant -> {
                    BigDecimal recommended = forecastRepository.findByTenantIdAndVariantId(
                                    TenantContext.requireTenantId(), variant.getId())
                            .map(DemandForecast::getRecommendedPoQty)
                            .orElse(variant.getReorderPoint());
                    return new LowStockItemResponse(
                            variant.getId(),
                            variant.getSku(),
                            productNames.getOrDefault(variant.getProductId(), variant.getSku()),
                            availableByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO),
                            recommended);
                })
                .filter(item -> item.available().compareTo(item.reorderPoint()) < 0)
                .sorted(Comparator.comparing(item -> item.available().subtract(item.reorderPoint())))
                .limit(6)
                .toList();
    }

    @GetMapping("/work-queue")
    public WorkQueueResponse workQueue() {
        TenantContext.requireTenantId();

        long needsAllocation = salesOrderRepository.findAll().stream()
                .filter(order -> "CONFIRMED".equals(order.getStatus()))
                .count();
        long readyToInvoice = salesOrderRepository.findAll().stream()
                .filter(order -> List.of("ALLOCATED", "SHIPPED").contains(order.getStatus()))
                .count();
        long unpaidInvoices = invoiceRepository.findAll().stream()
                .filter(invoice -> List.of("OPEN", "PARTIALLY_PAID").contains(invoice.getStatus()))
                .count();

        Map<UUID, BigDecimal> availableByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAvailable,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        long lowStock = variantRepository.findAll().stream()
                .filter(variant -> availableByVariant
                        .getOrDefault(variant.getId(), BigDecimal.ZERO)
                        .compareTo(variant.getReorderPoint()) < 0)
                .count();

        return new WorkQueueResponse(needsAllocation, readyToInvoice, unpaidInvoices, lowStock);
    }

    @GetMapping("/kpi-trends")
    public KpiTrendsResponse kpiTrends() {
        TenantContext.requireTenantId();

        List<String> openStatuses = List.of("CONFIRMED", "ALLOCATED", "PARTIALLY_SHIPPED");
        List<String> unpaidStatuses = List.of("OPEN", "PARTIALLY_PAID");

        long openOrders = salesOrderRepository.findAll().stream()
                .filter(order -> openStatuses.contains(order.getStatus()))
                .count();
        long unpaidInvoices = invoiceRepository.findAll().stream()
                .filter(invoice -> unpaidStatuses.contains(invoice.getStatus()))
                .count();

        Map<UUID, BigDecimal> availableByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAvailable,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        long lowStock = variantRepository.findAll().stream()
                .filter(variant -> availableByVariant
                        .getOrDefault(variant.getId(), BigDecimal.ZERO)
                        .compareTo(variant.getReorderPoint()) < 0)
                .count();

        BigDecimal avgVelocity = forecastRepository.findAll().stream()
                .map(DemandForecast::getVelocity30d)
                .filter(v -> v != null && v.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int forecastCount = (int) forecastRepository.findAll().stream()
                .filter(f -> f.getVelocity30d() != null && f.getVelocity30d().signum() > 0)
                .count();

        BigDecimal meanVelocity = forecastCount > 0
                ? avgVelocity.divide(new BigDecimal(forecastCount), 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new KpiTrendsResponse(
                meanVelocity.signum() > 0 ? "UP" : "FLAT",
                lowStock > 0 ? "UP" : "DOWN",
                openOrders > 0 ? "UP" : "FLAT",
                unpaidInvoices > 0 ? "UP" : "DOWN"
        );
    }

    @GetMapping("/low-stock-velocity")
    public List<VelocityPointResponse> lowStockVelocity() {
        TenantContext.requireTenantId();

        Map<UUID, BigDecimal> availableByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAvailable,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        List<ProductVariant> lowStockVariants = variantRepository.findAll().stream()
                .filter(variant -> availableByVariant
                        .getOrDefault(variant.getId(), BigDecimal.ZERO)
                        .compareTo(variant.getReorderPoint()) < 0)
                .toList();

        BigDecimal totalDailyDepletion = lowStockVariants.stream()
                .map(v -> forecastRepository.findByTenantIdAndVariantId(TenantContext.requireTenantId(), v.getId())
                        .map(DemandForecast::getVelocity30d)
                        .orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<VelocityPointResponse> points = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        BigDecimal stockUnits = lowStockVariants.stream()
                .map(v -> availableByVariant.getOrDefault(v.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            BigDecimal projected = stockUnits.add(totalDailyDepletion.multiply(new BigDecimal(i)));
            points.add(new VelocityPointResponse(day.toString(), projected.setScale(2, java.math.RoundingMode.HALF_UP)));
        }
        return points;
    }

    @GetMapping("/stockout-projections")
    public List<StockoutProjectionResponse> stockoutProjections() {
        UUID tenantId = TenantContext.requireTenantId();

        Map<UUID, BigDecimal> availableByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAvailable,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        return variantRepository.findAll().stream()
                .map(variant -> {
                    BigDecimal available = availableByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
                    BigDecimal velocity = forecastRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                            .map(DemandForecast::getVelocity30d)
                            .orElse(BigDecimal.ZERO);
                    LocalDate projected = null;
                    if (velocity != null && velocity.signum() > 0 && available.signum() >= 0) {
                        int days = available.divide(velocity, 0, java.math.RoundingMode.FLOOR).intValue();
                        projected = LocalDate.now(ZoneOffset.UTC).plusDays(Math.max(days, 0));
                    }
                    return new StockoutProjectionResponse(
                            variant.getId(),
                            variant.getSku(),
                            available,
                            velocity != null ? velocity : BigDecimal.ZERO,
                            projected);
                })
                .filter(row -> row.velocity30d().signum() > 0)
                .sorted(Comparator.comparing(
                        StockoutProjectionResponse::projectedStockoutDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public record WorkQueueResponse(
            long needsAllocation,
            long readyToInvoice,
            long unpaidInvoices,
            long lowStockItems
    ) {
    }

    public record KpiTrendsResponse(
            String stockValueTrend,
            String lowStockTrend,
            String openOrdersTrend,
            String unpaidInvoicesTrend
    ) {
    }

    public record VelocityPointResponse(String date, BigDecimal availableUnits) {
    }

    public record StockoutProjectionResponse(
            UUID variantId,
            String sku,
            BigDecimal available,
            BigDecimal velocity30d,
            LocalDate projectedStockoutDate
    ) {
    }

    public record RecentOrderResponse(
            UUID id,
            String number,
            String customerName,
            String status,
            Instant createdAt
    ) {
    }

    public record LowStockItemResponse(
            UUID variantId,
            String sku,
            String productName,
            BigDecimal available,
            BigDecimal reorderPoint
    ) {
    }
}

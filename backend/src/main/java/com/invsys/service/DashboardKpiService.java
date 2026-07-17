package com.invsys.service;

import com.invsys.api.dto.DashboardStatsResponse;
import com.invsys.domain.DashboardKpiSnapshot;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.DashboardKpiSnapshotRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CQRS read side for dashboard KPIs. Hot path reads {@code dashboard_kpi_snapshots};
 * OLTP aggregates run only when refreshing the projection.
 */
@Service
public class DashboardKpiService {

    static final List<String> OPEN_ORDER_STATUSES = List.of("CONFIRMED", "ALLOCATED", "PARTIALLY_SHIPPED");
    static final List<String> UNPAID_INVOICE_STATUSES = List.of("OPEN", "PARTIALLY_PAID");

    private final DashboardKpiSnapshotRepository snapshotRepository;
    private final InventoryLevelRepository levelRepository;
    private final ProductVariantRepository variantRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public DashboardKpiService(DashboardKpiSnapshotRepository snapshotRepository,
                               InventoryLevelRepository levelRepository,
                               ProductVariantRepository variantRepository,
                               SalesOrderRepository salesOrderRepository,
                               InvoiceRepository invoiceRepository,
                               TenantSettingsRepository tenantSettingsRepository) {
        this.snapshotRepository = snapshotRepository;
        this.levelRepository = levelRepository;
        this.variantRepository = variantRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.invoiceRepository = invoiceRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    @Transactional
    public DashboardStatsResponse readStats() {
        UUID tenantId = TenantContext.requireTenantId();
        return snapshotRepository.findByTenantId(tenantId)
                .map(this::toResponse)
                .orElseGet(() -> toResponse(refresh(tenantId, "LAZY_WARM")));
    }

    @Transactional
    public DashboardKpiSnapshot refresh(UUID tenantId, String sourceEventType) {
        TenantContext.setTenantId(tenantId);
        Aggregates aggregates = computeFromOltp();
        DashboardKpiSnapshot snapshot = snapshotRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    DashboardKpiSnapshot created = new DashboardKpiSnapshot();
                    created.setTenantId(tenantId);
                    return created;
                });
        snapshot.setStockValue(aggregates.stockValue());
        snapshot.setCurrency(aggregates.currency());
        snapshot.setLowStockCount(aggregates.lowStockCount());
        snapshot.setOpenOrdersCount(aggregates.openOrdersCount());
        snapshot.setUnpaidInvoicesCount(aggregates.unpaidInvoicesCount());
        snapshot.setSourceEventType(sourceEventType);
        snapshot.setRefreshedAt(Instant.now());
        return snapshotRepository.save(snapshot);
    }

    private Aggregates computeFromOltp() {
        long openOrdersCount = salesOrderRepository.findAll().stream()
                .filter(order -> OPEN_ORDER_STATUSES.contains(order.getStatus()))
                .count();
        long unpaidInvoicesCount = invoiceRepository.findAll().stream()
                .filter(invoice -> UNPAID_INVOICE_STATUSES.contains(invoice.getStatus()))
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
                        .multiply(variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long lowStockCount = variants.stream()
                .filter(variant -> availableByVariant
                        .getOrDefault(variant.getId(), BigDecimal.ZERO)
                        .compareTo(variant.getReorderPoint() != null ? variant.getReorderPoint() : BigDecimal.ZERO) < 0)
                .count();

        String currency = tenantSettingsRepository.findAll().stream()
                .findFirst()
                .map(settings -> settings.getSettings().get("currency"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse("USD");

        return new Aggregates(stockValue, currency, lowStockCount, openOrdersCount, unpaidInvoicesCount);
    }

    private DashboardStatsResponse toResponse(DashboardKpiSnapshot snapshot) {
        return new DashboardStatsResponse(
                snapshot.getStockValue(),
                snapshot.getCurrency(),
                snapshot.getLowStockCount(),
                snapshot.getOpenOrdersCount(),
                snapshot.getUnpaidInvoicesCount());
    }

    private record Aggregates(
            BigDecimal stockValue,
            String currency,
            long lowStockCount,
            long openOrdersCount,
            long unpaidInvoicesCount
    ) {
    }
}

package com.invsys.service;

import com.invsys.domain.DemandForecast;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ForecastingService {

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final DemandForecastRepository forecastRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;

    public ForecastingService(ProductVariantRepository variantRepository,
                              InventoryLevelRepository levelRepository,
                              DemandForecastRepository forecastRepository,
                              ProductRepository productRepository,
                              SupplierRepository supplierRepository,
                              PurchaseOrderRepository purchaseOrderRepository,
                              PurchaseOrderLineRepository purchaseOrderLineRepository) {
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
        this.forecastRepository = forecastRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
    }

    public List<ForecastAlert> alerts() {
        UUID tenantId = TenantContext.requireTenantId();

        Map<UUID, BigDecimal> availableByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAvailable,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        Map<UUID, String> productNames = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));

        Map<UUID, String> supplierNames = supplierRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));

        return variantRepository.findAll().stream()
                .map(variant -> {
                    BigDecimal available = availableByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
                    DemandForecast forecast = forecastRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                            .orElse(null);
                    BigDecimal recommended = forecast != null ? forecast.getRecommendedPoQty() : variant.getReorderPoint();
                    BigDecimal velocity = forecast != null ? forecast.getVelocity30d() : BigDecimal.ZERO;
                    UUID supplierId = variant.getDefaultSupplierId();
                    return new ForecastAlert(
                            variant.getId(),
                            variant.getSku(),
                            productNames.getOrDefault(variant.getProductId(), variant.getSku()),
                            available,
                            variant.getReorderPoint(),
                            recommended,
                            velocity,
                            supplierId,
                            supplierId != null ? supplierNames.getOrDefault(supplierId, "—") : null,
                            variant.getSupplierLeadTimeDays(),
                            forecast != null ? forecast.getCalculatedAt() : null);
                })
                .filter(alert -> alert.available().compareTo(alert.reorderPoint()) < 0
                        || alert.recommendedPoQty().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(ForecastAlert::recommendedPoQty).reversed())
                .toList();
    }

    @Transactional
    public List<PurchaseOrder> createDraftPo(List<UUID> variantIds) {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, ProductVariant> variantsById = variantRepository.findAll().stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v, (a, b) -> a));

        Map<UUID, List<UUID>> bySupplier = new LinkedHashMap<>();
        for (UUID variantId : variantIds) {
            ProductVariant variant = variantsById.get(variantId);
            if (variant == null) {
                continue;
            }
            UUID supplierId = variant.getDefaultSupplierId();
            if (supplierId == null) {
                supplierId = supplierRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                        .findFirst()
                        .map(Supplier::getId)
                        .orElseThrow(() -> new IllegalStateException("No supplier configured"));
            }
            bySupplier.computeIfAbsent(supplierId, ignored -> new ArrayList<>()).add(variantId);
        }

        List<PurchaseOrder> created = new ArrayList<>();
        long stamp = Instant.now().toEpochMilli();
        int index = 0;
        for (Map.Entry<UUID, List<UUID>> entry : bySupplier.entrySet()) {
            PurchaseOrder po = new PurchaseOrder();
            po.setTenantId(tenantId);
            po.setSupplierId(entry.getKey());
            po.setNumber("PO-FORECAST-" + stamp + (index > 0 ? "-" + index : ""));
            po.setStatus("DRAFT");
            po = purchaseOrderRepository.save(po);

            for (UUID variantId : entry.getValue()) {
                BigDecimal qty = forecastRepository.findByTenantIdAndVariantId(tenantId, variantId)
                        .map(DemandForecast::getRecommendedPoQty)
                        .filter(q -> q.compareTo(BigDecimal.ZERO) > 0)
                        .orElse(variantsById.get(variantId).getReorderPoint());

                PurchaseOrderLine line = new PurchaseOrderLine();
                line.setTenantId(tenantId);
                line.setPurchaseOrderId(po.getId());
                line.setVariantId(variantId);
                line.setQtyOrdered(qty);
                line.setUnitCost(BigDecimal.ZERO);
                purchaseOrderLineRepository.save(line);
            }
            created.add(po);
            index++;
        }
        return created;
    }

    public record ForecastAlert(
            UUID variantId,
            String sku,
            String productName,
            BigDecimal available,
            BigDecimal reorderPoint,
            BigDecimal recommendedPoQty,
            BigDecimal velocity30d,
            UUID defaultSupplierId,
            String defaultSupplierName,
            int supplierLeadTimeDays,
            Instant calculatedAt
    ) {
    }
}

package com.invsys.service;

import com.invsys.domain.DemandForecast;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ForecastingWorker {

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final DemandForecastRepository forecastRepository;
    private final TenantRepository tenantRepository;
    private final ForecastingInferenceService inferenceService;

    public ForecastingWorker(ProductVariantRepository variantRepository,
                             InventoryLevelRepository levelRepository,
                             SalesOrderLineRepository salesOrderLineRepository,
                             PurchaseOrderLineRepository purchaseOrderLineRepository,
                             PurchaseOrderRepository purchaseOrderRepository,
                             DemandForecastRepository forecastRepository,
                             TenantRepository tenantRepository,
                             ForecastingInferenceService inferenceService) {
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.forecastRepository = forecastRepository;
        this.tenantRepository = tenantRepository;
        this.inferenceService = inferenceService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runForecast() {
        for (UUID tenantId : tenantRepository.findAll().stream().map(t -> t.getId()).toList()) {
            calculateForTenant(tenantId);
        }
    }

    @Transactional
    public void calculateForTenant(UUID tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

            Map<UUID, BigDecimal> velocityByVariant = new HashMap<>();
            for (Object[] row : salesOrderLineRepository.sumQtyOrderedByVariantSince(tenantId, since)) {
                UUID variantId = (UUID) row[0];
                BigDecimal total = (BigDecimal) row[1];
                velocityByVariant.put(variantId, total.divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP));
            }

            Map<UUID, BigDecimal> onHand = new HashMap<>();
            for (InventoryLevel level : levelRepository.findAll()) {
                onHand.merge(level.getVariantId(), level.getOnHand(), BigDecimal::add);
            }

            Map<UUID, BigDecimal> incomingPo = incomingPoByVariant();

            for (ProductVariant variant : variantRepository.findAll()) {
                if (isExcludedFromReplenishment(variant)) {
                    DemandForecast forecast = forecastRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                            .orElseGet(() -> {
                                DemandForecast f = new DemandForecast();
                                f.setTenantId(tenantId);
                                f.setVariantId(variant.getId());
                                return f;
                            });
                    forecast.setRecommendedPoQty(BigDecimal.ZERO);
                    forecast.setVelocity30d(velocityByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO));
                    forecast.setSeasonalityIndex(BigDecimal.ONE);
                    forecast.setConfidenceScore(BigDecimal.ONE);
                    Map<String, Object> signals = new LinkedHashMap<>();
                    signals.put("model", "lifecycle_exclusion");
                    signals.put("lifecycleStatus", variant.getLifecycleStatus());
                    signals.put("excludedFromReplenishment", true);
                    forecast.setExternalSignals(signals);
                    forecast.setCalculatedAt(Instant.now());
                    forecastRepository.save(forecast);
                    continue;
                }

                BigDecimal velocity = velocityByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
                BigDecimal stock = onHand.getOrDefault(variant.getId(), BigDecimal.ZERO);
                BigDecimal incoming = incomingPo.getOrDefault(variant.getId(), BigDecimal.ZERO);

                ForecastingInferenceService.InferenceResult result = inferenceService.infer(
                        variant, velocity, stock, incoming);

                DemandForecast forecast = forecastRepository.findByTenantIdAndVariantId(tenantId, variant.getId())
                        .orElseGet(() -> {
                            DemandForecast f = new DemandForecast();
                            f.setTenantId(tenantId);
                            f.setVariantId(variant.getId());
                            return f;
                        });
                inferenceService.applyToForecast(forecast, result);
                forecast.setCalculatedAt(Instant.now());
                forecastRepository.save(forecast);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean isExcludedFromReplenishment(ProductVariant variant) {
        String status = variant.getLifecycleStatus();
        return status != null && Set.of("PHASE_OUT", "DISCONTINUED").contains(status.trim().toUpperCase());
    }

    private Map<UUID, BigDecimal> incomingPoByVariant() {
        List<UUID> openPoIds = purchaseOrderRepository.findAll().stream()
                .filter(po -> List.of("DRAFT", "SUBMITTED", "PARTIALLY_RECEIVED").contains(po.getStatus()))
                .map(PurchaseOrder::getId)
                .toList();
        Map<UUID, BigDecimal> incoming = new HashMap<>();
        for (PurchaseOrderLine line : purchaseOrderLineRepository.findAll()) {
            if (!openPoIds.contains(line.getPurchaseOrderId())) {
                continue;
            }
            BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyReceived());
            incoming.merge(line.getVariantId(), remaining, BigDecimal::add);
        }
        return incoming;
    }
}

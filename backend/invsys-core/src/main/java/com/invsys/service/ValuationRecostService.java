package com.invsys.service;

import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Detached worker: rebuilds product_variants.avg_cost from append-only ledger history
 * (moving-average or FIFO shadow index) without blocking online receive/ship paths.
 */
@Service
public class ValuationRecostService {

    private static final Logger log = LoggerFactory.getLogger(ValuationRecostService.class);

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository settingsRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final ProductVariantRepository variantRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor virtualThreadExecutor;

    public ValuationRecostService(TenantRepository tenantRepository,
                                  TenantSettingsRepository settingsRepository,
                                  InventoryLedgerRepository ledgerRepository,
                                  ProductVariantRepository variantRepository,
                                  PlatformTransactionManager transactionManager,
                                  @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.ledgerRepository = ledgerRepository;
        this.variantRepository = variantRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Scheduled(fixedDelayString = "${invsys.costing.recost-interval-ms:1800000}")
    public void scheduleRecost() {
        for (UUID tenantId : tenantRepository.findAll().stream().map(t -> t.getId()).toList()) {
            virtualThreadExecutor.execute(() -> recostTenant(tenantId));
        }
    }

    /** Invoked by outbox handler when costing_method changes. */
    public void recostTenant(UUID tenantId) {
        UUID previousTenant = TenantContext.getTenantId().orElse(null);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                TenantContext.setTenantId(tenantId);
                String method = settingsRepository.findByTenantId(tenantId)
                        .map(TenantSettings::getSettings)
                        .map(s -> s.get("costing_method"))
                        .map(String::valueOf)
                        .orElse("MOVING_AVERAGE");
                List<InventoryLedger> ledger = ledgerRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
                if ("FIFO".equalsIgnoreCase(method)) {
                    rebuildFifo(tenantId, ledger);
                } else {
                    rebuildMovingAverage(tenantId, ledger);
                }
            });
        } catch (Exception ex) {
            log.warn("Recost failed for tenant {}: {}", tenantId, ex.getMessage());
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void rebuildMovingAverage(UUID tenantId, List<InventoryLedger> ledger) {
        Map<UUID, BigDecimal> qty = new HashMap<>();
        Map<UUID, BigDecimal> avg = new HashMap<>();
        Set<UUID> touched = new HashSet<>();
        for (InventoryLedger entry : ledger) {
            UUID variantId = entry.getVariantId();
            touched.add(variantId);
            BigDecimal onHand = qty.getOrDefault(variantId, BigDecimal.ZERO);
            BigDecimal currentAvg = avg.getOrDefault(variantId, BigDecimal.ZERO);
            BigDecimal delta = entry.getQuantityDelta() != null ? entry.getQuantityDelta() : BigDecimal.ZERO;
            if (delta.signum() > 0) {
                BigDecimal unit = entry.getUnitCost() != null ? entry.getUnitCost() : BigDecimal.ZERO;
                BigDecimal newQty = onHand.add(delta);
                BigDecimal newAvg = newQty.signum() == 0
                        ? BigDecimal.ZERO
                        : onHand.multiply(currentAvg).add(delta.multiply(unit))
                        .divide(newQty, 4, RoundingMode.HALF_UP);
                qty.put(variantId, newQty);
                avg.put(variantId, newAvg);
            } else if (delta.signum() < 0) {
                qty.put(variantId, onHand.add(delta).max(BigDecimal.ZERO));
                // Ship/consume keeps moving average; no avg change.
            }
        }
        persistAvgs(tenantId, touched, avg);
    }

    private void rebuildFifo(UUID tenantId, List<InventoryLedger> ledger) {
        Map<UUID, Deque<Layer>> layers = new HashMap<>();
        Set<UUID> touched = new HashSet<>();
        for (InventoryLedger entry : ledger) {
            UUID variantId = entry.getVariantId();
            touched.add(variantId);
            Deque<Layer> queue = layers.computeIfAbsent(variantId, id -> new ArrayDeque<>());
            BigDecimal delta = entry.getQuantityDelta() != null ? entry.getQuantityDelta() : BigDecimal.ZERO;
            if (delta.signum() > 0) {
                BigDecimal unit = entry.getUnitCost() != null ? entry.getUnitCost() : BigDecimal.ZERO;
                queue.addLast(new Layer(delta, unit));
            } else if (delta.signum() < 0) {
                BigDecimal remaining = delta.abs();
                while (remaining.signum() > 0 && !queue.isEmpty()) {
                    Layer head = queue.peekFirst();
                    if (head.qty.compareTo(remaining) <= 0) {
                        remaining = remaining.subtract(head.qty);
                        queue.removeFirst();
                    } else {
                        head.qty = head.qty.subtract(remaining);
                        remaining = BigDecimal.ZERO;
                    }
                }
            }
        }
        Map<UUID, BigDecimal> avg = new HashMap<>();
        for (Map.Entry<UUID, Deque<Layer>> e : layers.entrySet()) {
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalValue = BigDecimal.ZERO;
            for (Layer layer : e.getValue()) {
                totalQty = totalQty.add(layer.qty);
                totalValue = totalValue.add(layer.qty.multiply(layer.unitCost));
            }
            avg.put(e.getKey(), totalQty.signum() == 0
                    ? BigDecimal.ZERO
                    : totalValue.divide(totalQty, 4, RoundingMode.HALF_UP));
        }
        persistAvgs(tenantId, touched, avg);
    }

    private void persistAvgs(UUID tenantId, Set<UUID> touched, Map<UUID, BigDecimal> avg) {
        for (UUID variantId : touched) {
            ProductVariant variant = variantRepository.findById(variantId).orElse(null);
            if (variant == null || !tenantId.equals(variant.getTenantId())) {
                continue;
            }
            BigDecimal next = avg.getOrDefault(variantId, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            if (variant.getAvgCost() == null || variant.getAvgCost().compareTo(next) != 0) {
                variant.setAvgCost(next);
                variantRepository.save(variant);
            }
        }
    }

    private static final class Layer {
        BigDecimal qty;
        final BigDecimal unitCost;

        Layer(BigDecimal qty, BigDecimal unitCost) {
            this.qty = qty;
            this.unitCost = unitCost;
        }
    }
}

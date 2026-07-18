package com.invsys.service;

import com.invsys.domain.BinReplenishmentRule;
import com.invsys.domain.DemandForecast;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.WaveReplenishmentTrigger;
import com.invsys.repository.BinReplenishmentRuleRepository;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.WaveReplenishmentTriggerRepository;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Predictive forward-staging: when 48-hour projected demand (from {@code demand_forecasts}
 * velocity) exceeds pick-face on-hand / min threshold, opens an ACTIVE
 * {@link WaveReplenishmentTrigger} that feeds {@link TaskOrchestratorService}.
 */
@Component
@ConditionalOnProperty(name = "invsys.replenishment.predictive.enabled", havingValue = "true", matchIfMissing = true)
public class PredictiveReplenishmentWorker {

    private static final Logger log = LoggerFactory.getLogger(PredictiveReplenishmentWorker.class);
    private static final BigDecimal TWO_DAYS = new BigDecimal("2");

    private final TenantRepository tenantRepository;
    private final BinReplenishmentRuleRepository ruleRepository;
    private final DemandForecastRepository forecastRepository;
    private final InventoryLevelRepository levelRepository;
    private final WaveReplenishmentTriggerRepository triggerRepository;
    private final ExecutorService virtualThreadExecutor;

    public PredictiveReplenishmentWorker(
            TenantRepository tenantRepository,
            BinReplenishmentRuleRepository ruleRepository,
            DemandForecastRepository forecastRepository,
            InventoryLevelRepository levelRepository,
            WaveReplenishmentTriggerRepository triggerRepository,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.tenantRepository = tenantRepository;
        this.ruleRepository = ruleRepository;
        this.forecastRepository = forecastRepository;
        this.levelRepository = levelRepository;
        this.triggerRepository = triggerRepository;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Scheduled(fixedDelayString = "${invsys.replenishment.predictive.poll-interval-ms:300000}")
    public void scheduleScan() {
        virtualThreadExecutor.execute(this::scanAllTenants);
    }

    void scanAllTenants() {
        for (UUID tenantId : tenantRepository.findAll().stream().map(t -> t.getId()).toList()) {
            try {
                evaluateTenant(tenantId);
            } catch (Exception ex) {
                log.warn("Predictive replenishment failed for tenant {}: {}", tenantId, ex.toString());
            }
        }
    }

    @Transactional
    public int evaluateTenant(UUID tenantId) {
        Optional<UUID> previous = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            int created = 0;
            List<BinReplenishmentRule> rules = ruleRepository.findByTenantId(tenantId);
            for (BinReplenishmentRule rule : rules) {
                if (upsertTriggerIfNeeded(tenantId, rule)) {
                    created++;
                }
            }
            if (created > 0 && log.isDebugEnabled()) {
                log.debug("Opened {} predictive replenishment triggers for tenant {}", created, tenantId);
            }
            return created;
        } finally {
            if (previous.isPresent()) {
                TenantContext.setTenantId(previous.get());
            } else {
                TenantContext.clear();
            }
        }
    }

    private boolean upsertTriggerIfNeeded(UUID tenantId, BinReplenishmentRule rule) {
        BigDecimal onHand = levelRepository
                .findByTenantIdAndVariantId(tenantId, rule.getVariantId())
                .stream()
                .filter(l -> rule.getLocationId().equals(l.getLocationId()))
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal velocity30d = forecastRepository
                .findByTenantIdAndVariantId(tenantId, rule.getVariantId())
                .map(DemandForecast::getVelocity30d)
                .orElse(BigDecimal.ZERO);

        // Projected 48h demand from daily velocity (velocity_30d is units/day).
        BigDecimal projected48h = velocity30d.multiply(TWO_DAYS).setScale(4, RoundingMode.HALF_UP);
        BigDecimal min = rule.getMinQuantity() != null ? rule.getMinQuantity() : BigDecimal.ZERO;
        BigDecimal max = rule.getMaxQuantity() != null ? rule.getMaxQuantity() : min;

        boolean demandBreachesBin = projected48h.compareTo(onHand) > 0 && projected48h.compareTo(min) >= 0;
        boolean belowMin = onHand.compareTo(min) < 0;
        if (!demandBreachesBin && !belowMin) {
            return false;
        }

        var existing = triggerRepository.findByTenantIdAndVariantIdAndLocationIdAndStatusIn(
                tenantId, rule.getVariantId(), rule.getLocationId(), List.of("PENDING", "ACTIVE"));
        if (existing.isPresent()) {
            WaveReplenishmentTrigger open = existing.get();
            open.setCurrentBinQty(onHand);
            open.setProjectedDemand(projected48h);
            open.setMinThreshold(min);
            open.setTargetQty(max.subtract(onHand).max(BigDecimal.ZERO));
            open.setStatus("ACTIVE");
            triggerRepository.save(open);
            return false;
        }

        WaveReplenishmentTrigger trigger = new WaveReplenishmentTrigger();
        trigger.setTenantId(tenantId);
        trigger.setVariantId(rule.getVariantId());
        trigger.setLocationId(rule.getLocationId());
        trigger.setCurrentBinQty(onHand);
        trigger.setProjectedDemand(projected48h);
        trigger.setMinThreshold(min);
        trigger.setTargetQty(max.subtract(onHand).max(min.subtract(onHand).max(BigDecimal.ZERO)));
        trigger.setStatus("ACTIVE");
        triggerRepository.save(trigger);
        return true;
    }
}

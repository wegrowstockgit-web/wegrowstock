package com.invsys.service;

import com.invsys.domain.TenantSettings;
import com.invsys.core.integration.OutboxService;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SettingsService {

    private final TenantSettingsRepository repository;
    private final OutboxService outboxService;
    private final TenantSettingsCacheService cacheService;

    public SettingsService(TenantSettingsRepository repository,
                           OutboxService outboxService,
                           TenantSettingsCacheService cacheService) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.cacheService = cacheService;
    }

    public Map<String, Object> getSettings() {
        UUID tenantId = TenantContext.requireTenantId();
        return cacheService.get(tenantId).orElseGet(() -> {
            Map<String, Object> loaded = repository.findByTenantId(tenantId)
                    .map(this::toResponseMap)
                    .orElse(Map.of());
            if (!loaded.isEmpty()) {
                cacheService.put(tenantId, loaded);
            }
            return loaded;
        });
    }

    @Transactional
    public Map<String, Object> patchSettings(Map<String, Object> patch) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseGet(() -> TenantSettings.withDefaults(tenantId));
        String previousCosting = stringOrNull(settings.getSettings().get("costing_method"));
        if (patch != null) {
            if (patch.size() > 64) {
                throw new com.invsys.core.common.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "SETTINGS_TOO_LARGE",
                        "Settings patch cannot contain more than 64 keys");
            }
            settings.getSettings().putAll(patch);
            applyTypedColumns(settings, patch);
        }
        repository.save(settings);
        // Invalidate now; @PostUpdate also evicts after commit so we never re-warm in-tx.
        cacheService.invalidate(tenantId);

        if (patch != null && patch.containsKey("costing_method")) {
            String nextCosting = stringOrNull(settings.getSettings().get("costing_method"));
            if (!Objects.equals(previousCosting, nextCosting)) {
                outboxService.append("TENANT", tenantId, "COSTING_METHOD_CHANGED", Map.of(
                        "previous", previousCosting != null ? previousCosting : "",
                        "costingMethod", nextCosting != null ? nextCosting : "MOVING_AVERAGE"));
            }
        }
        return toResponseMap(settings);
    }

    @Transactional
    public Map<String, Object> flushCache() {
        UUID tenantId = TenantContext.requireTenantId();
        cacheService.invalidate(tenantId);
        return getSettings();
    }

    private Map<String, Object> toResponseMap(TenantSettings settings) {
        Map<String, Object> map = new java.util.HashMap<>(settings.getSettings());
        map.put("blind_cycle_counts", settings.isBlindCycleCounts());
        map.put("max_auto_adjust_value", settings.getMaxAutoAdjustValue());
        map.put("rma_auto_approve_max_value", settings.getRmaAutoApproveMaxValue());
        map.put("predictive_replenishment_enabled", settings.isPredictiveReplenishmentEnabled());
        return map;
    }

    private void applyTypedColumns(TenantSettings settings, Map<String, Object> patch) {
        if (patch.containsKey("blind_cycle_counts")) {
            settings.setBlindCycleCounts(Boolean.parseBoolean(String.valueOf(patch.get("blind_cycle_counts"))));
        }
        if (patch.containsKey("max_auto_adjust_value")) {
            Object raw = patch.get("max_auto_adjust_value");
            if (raw != null) {
                settings.setMaxAutoAdjustValue(new java.math.BigDecimal(String.valueOf(raw)));
            }
        }
        if (patch.containsKey("rma_auto_approve_max_value")) {
            Object raw = patch.get("rma_auto_approve_max_value");
            if (raw != null) {
                settings.setRmaAutoApproveMaxValue(new java.math.BigDecimal(String.valueOf(raw)));
            }
        }
        if (patch.containsKey("predictive_replenishment_enabled")) {
            settings.setPredictiveReplenishmentEnabled(
                    Boolean.parseBoolean(String.valueOf(patch.get("predictive_replenishment_enabled"))));
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

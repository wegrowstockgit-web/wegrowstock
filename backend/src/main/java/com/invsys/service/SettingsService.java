package com.invsys.service;

import com.invsys.domain.TenantSettings;
import com.invsys.integration.OutboxService;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SettingsService {

    private final TenantSettingsRepository repository;
    private final OutboxService outboxService;

    public SettingsService(TenantSettingsRepository repository, OutboxService outboxService) {
        this.repository = repository;
        this.outboxService = outboxService;
    }

    public Map<String, Object> getSettings() {
        return repository.findByTenantId(TenantContext.requireTenantId())
                .map(this::toResponseMap)
                .orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> patchSettings(Map<String, Object> patch) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseGet(() -> TenantSettings.withDefaults(tenantId));
        String previousCosting = stringOrNull(settings.getSettings().get("costing_method"));
        if (patch != null) {
            settings.getSettings().putAll(patch);
            applyTypedColumns(settings, patch);
        }
        repository.save(settings);

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

    private Map<String, Object> toResponseMap(TenantSettings settings) {
        Map<String, Object> map = new java.util.HashMap<>(settings.getSettings());
        map.put("blind_cycle_counts", settings.isBlindCycleCounts());
        map.put("max_auto_adjust_value", settings.getMaxAutoAdjustValue());
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
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

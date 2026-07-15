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
                .map(TenantSettings::getSettings)
                .orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> patchSettings(Map<String, Object> patch) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSettings settings = repository.findByTenantId(tenantId)
                .orElseGet(() -> TenantSettings.withDefaults(tenantId));
        String previousCosting = stringOrNull(settings.getSettings().get("costing_method"));
        settings.getSettings().putAll(patch);
        repository.save(settings);

        if (patch != null && patch.containsKey("costing_method")) {
            String nextCosting = stringOrNull(settings.getSettings().get("costing_method"));
            if (!Objects.equals(previousCosting, nextCosting)) {
                outboxService.append("TENANT", tenantId, "COSTING_METHOD_CHANGED", Map.of(
                        "previous", previousCosting != null ? previousCosting : "",
                        "costingMethod", nextCosting != null ? nextCosting : "MOVING_AVERAGE"));
            }
        }
        return settings.getSettings();
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

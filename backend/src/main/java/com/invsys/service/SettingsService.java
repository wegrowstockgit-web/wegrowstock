package com.invsys.service;

import com.invsys.domain.TenantSettings;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SettingsService {

    private final TenantSettingsRepository repository;

    public SettingsService(TenantSettingsRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> getSettings() {
        return repository.findByTenantId(TenantContext.requireTenantId())
                .map(TenantSettings::getSettings)
                .orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> patchSettings(Map<String, Object> patch) {
        TenantSettings settings = repository.findByTenantId(TenantContext.requireTenantId())
                .orElseGet(() -> TenantSettings.withDefaults(TenantContext.requireTenantId()));
        settings.getSettings().putAll(patch);
        repository.save(settings);
        return settings.getSettings();
    }
}

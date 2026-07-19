package com.invsys.domain;

import com.invsys.service.TenantSettingsCacheService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Forces Redis/local tenant settings cache flush whenever {@link TenantSettings} changes.
 */
@Component
public class TenantSettingsCacheEvictListener {

    private static TenantSettingsCacheService cacheService;

    @Autowired
    public void setCacheService(TenantSettingsCacheService cacheService) {
        TenantSettingsCacheEvictListener.cacheService = cacheService;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void evict(TenantSettings settings) {
        if (settings != null && settings.getTenantId() != null && cacheService != null) {
            cacheService.invalidate(settings.getTenantId());
        }
    }
}

package com.invsys;

import com.invsys.service.TenantProvisioningService;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestDataHelper {

    private final TenantProvisioningService provisioningService;

    public TestDataHelper(TenantProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    public UUID createTenant(String name, String slug) {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setBootstrap(true);
        TenantContext.setTenantId(tenantId);
        try {
            return provisioningService.createTenant(name, slug, tenantId);
        } finally {
            TenantContext.setBootstrap(false);
        }
    }
}

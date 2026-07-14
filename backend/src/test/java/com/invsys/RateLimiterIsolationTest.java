package com.invsys;

import com.invsys.integration.IntegrationRateLimiter;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class RateLimiterIsolationTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired IntegrationRateLimiter rateLimiter;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void rateLimitBucketsArePerTenant() {
        UUID tenantA = testDataHelper.createTenant("Rate A", "rate-a-" + UUID.randomUUID().toString().substring(0, 8));
        UUID tenantB = testDataHelper.createTenant("Rate B", "rate-b-" + UUID.randomUUID().toString().substring(0, 8));

        TenantContext.setTenantId(tenantA);
        for (int i = 0; i < 50; i++) {
            rateLimiter.tryAcquire("SHOPIFY", 1);
        }
        TenantContext.clear();

        TenantContext.setTenantId(tenantB);
        assertThatCode(() -> rateLimiter.tryAcquire("SHOPIFY", 1)).doesNotThrowAnyException();
    }
}

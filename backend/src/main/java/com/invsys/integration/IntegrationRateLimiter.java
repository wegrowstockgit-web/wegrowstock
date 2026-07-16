package com.invsys.integration;

import com.invsys.ratelimit.DistributedRateLimiter;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Per-tenant integration budgets keyed as {@code rate:{tenantId}:{system}} in Redis
 * (process-local fallback when Redis is disabled).
 */
@Service
public class IntegrationRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final Map<String, Integer> DEFAULT_CAPACITY = Map.of(
            "QUICKBOOKS", 500,
            "XERO", 60,
            "SHOPIFY", 1000,
            "AMAZON", 100,
            "EASYPOST", 200
    );

    private final DistributedRateLimiter distributedRateLimiter;

    public IntegrationRateLimiter(DistributedRateLimiter distributedRateLimiter) {
        this.distributedRateLimiter = distributedRateLimiter;
    }

    public void tryAcquire(String system, int tokens) {
        var tenantId = TenantContext.requireTenantId();
        String key = "rate:" + tenantId + ":" + system.toUpperCase();
        int capacity = DEFAULT_CAPACITY.getOrDefault(system.toUpperCase(), 100);
        distributedRateLimiter.tryAcquire(key, capacity, tokens, WINDOW);
    }
}

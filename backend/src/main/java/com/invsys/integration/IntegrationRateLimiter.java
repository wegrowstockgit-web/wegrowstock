package com.invsys.integration;

import com.invsys.config.IntegrationProperties;
import com.invsys.integration.domain.IntegrationRateBucket;
import com.invsys.integration.repository.IntegrationRateBucketRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class IntegrationRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final Map<String, BigDecimal> DEFAULT_CAPACITY = Map.of(
            "QUICKBOOKS", BigDecimal.valueOf(500),
            "XERO", BigDecimal.valueOf(60),
            "SHOPIFY", BigDecimal.valueOf(1000),
            "AMAZON", BigDecimal.valueOf(100),
            "EASYPOST", BigDecimal.valueOf(200)
    );

    private final IntegrationRateBucketRepository bucketRepository;

    public IntegrationRateLimiter(IntegrationRateBucketRepository bucketRepository) {
        this.bucketRepository = bucketRepository;
    }

    @Transactional
    public void tryAcquire(String system, int tokens) {
        var tenantId = TenantContext.requireTenantId();
        IntegrationRateBucket bucket = bucketRepository.findForUpdate(tenantId, system)
                .orElseGet(() -> createBucketOrFetch(tenantId, system));

        refreshWindowIfNeeded(bucket, system);

        BigDecimal cost = BigDecimal.valueOf(tokens);
        if (bucket.getTokensRemaining().compareTo(cost) < 0) {
            throw new com.invsys.common.ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED",
                    "Rate limit exceeded for " + system);
        }
        bucket.setTokensRemaining(bucket.getTokensRemaining().subtract(cost));
        bucketRepository.save(bucket);
    }

    private IntegrationRateBucket createBucketOrFetch(java.util.UUID tenantId, String system) {
        try {
            return createBucket(tenantId, system);
        } catch (DataIntegrityViolationException duplicate) {
            return bucketRepository.findForUpdate(tenantId, system)
                    .orElseThrow(() -> duplicate);
        }
    }

    private IntegrationRateBucket createBucket(java.util.UUID tenantId, String system) {
        IntegrationRateBucket bucket = new IntegrationRateBucket();
        bucket.setTenantId(tenantId);
        bucket.setSystem(system);
        bucket.setTokensRemaining(capacityFor(system));
        bucket.setWindowStart(Instant.now());
        return bucketRepository.save(bucket);
    }

    private void refreshWindowIfNeeded(IntegrationRateBucket bucket, String system) {
        Instant now = Instant.now();
        if (Duration.between(bucket.getWindowStart(), now).compareTo(WINDOW) >= 0) {
            bucket.setWindowStart(now);
            bucket.setTokensRemaining(capacityFor(system));
        }
    }

    private BigDecimal capacityFor(String system) {
        return DEFAULT_CAPACITY.getOrDefault(system, BigDecimal.valueOf(100));
    }
}

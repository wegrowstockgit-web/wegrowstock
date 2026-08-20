package com.invsys.ratelimit;

import com.invsys.core.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DistributedRateLimiterTest {

    private DistributedRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        limiter = new DistributedRateLimiter(provider);
    }

    @Test
    void localBucketAllowsWithinCapacityThenRejects() {
        String key = "rate:tenant-a:EASYPOST";
        Duration window = Duration.ofMinutes(1);
        assertThatCode(() -> limiter.tryAcquire(key, 2, 1, window)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.tryAcquire(key, 2, 1, window)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.tryAcquire(key, 2, 1, window))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void throttledTenantIsRejectedImmediately() {
        UUID tenantId = UUID.randomUUID();
        limiter.setTenantThrottle(tenantId, true, null);
        assertThatThrownBy(() -> limiter.tryAcquire("rate:" + tenantId + ":api", 100, 1, Duration.ofSeconds(1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("paused");
    }

    @Test
    void customRateLimitOverridesCapacity() {
        UUID tenantId = UUID.randomUUID();
        limiter.setTenantThrottle(tenantId, false, 1);
        String key = "rate:" + tenantId + ":api";
        Duration window = Duration.ofSeconds(1);
        assertThatCode(() -> limiter.tryAcquire(key, 100, 1, window)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.tryAcquire(key, 100, 1, window))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void shrinkingCustomRpsResetsWindowSoSecondRequestIsLimited() {
        UUID tenantId = UUID.randomUUID();
        String key = "rate:" + tenantId + ":api";
        Duration window = Duration.ofSeconds(1);
        assertThatCode(() -> limiter.tryAcquire(key, 100, 1, window)).doesNotThrowAnyException();
        limiter.setTenantThrottle(tenantId, false, 1);
        assertThatCode(() -> limiter.tryAcquire(key, 100, 1, window)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.tryAcquire(key, 100, 1, window))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}

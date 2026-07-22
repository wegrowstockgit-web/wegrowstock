package com.invsys.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedJobLockTest {

    @Test
    void localFallbackPreventsConcurrentLock() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        DistributedJobLock lock = new DistributedJobLock(provider);
        assertThat(lock.tryLock("job-a", Duration.ofMinutes(5))).isTrue();
        assertThat(lock.tryLock("job-a", Duration.ofMinutes(5))).isFalse();
        lock.unlock("job-a");
        assertThat(lock.tryLock("job-a", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void redisSetIfAbsentAndDelete() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("job-lock:job-b"), anyString(), any(Duration.class))).thenReturn(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);

        DistributedJobLock lock = new DistributedJobLock(provider);
        assertThat(lock.tryLock("job-b", Duration.ofHours(1))).isTrue();
        when(ops.setIfAbsent(eq("job-lock:job-b"), anyString(), any(Duration.class))).thenReturn(false);
        assertThat(lock.tryLock("job-b", Duration.ofHours(1))).isFalse();
        lock.unlock("job-b");
        verify(redis).delete("job-lock:job-b");
    }
}

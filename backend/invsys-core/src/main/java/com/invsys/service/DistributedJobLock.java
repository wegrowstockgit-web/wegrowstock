package com.invsys.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-instance job mutex. Prefers Redis {@code SET NX EX}; falls back to a
 * process-local map when Redis is disabled (dev / single replica).
 */
@Component
public class DistributedJobLock {

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, Boolean> local = new ConcurrentHashMap<>();

    public DistributedJobLock(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public boolean tryLock(String jobName, Duration ttl) {
        String key = key(jobName);
        if (redis != null) {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, Instant.now().toString(), ttl);
            return Boolean.TRUE.equals(acquired);
        }
        return local.putIfAbsent(key, Boolean.TRUE) == null;
    }

    public void unlock(String jobName) {
        String key = key(jobName);
        if (redis != null) {
            redis.delete(key);
            return;
        }
        local.remove(key);
    }

    private static String key(String jobName) {
        return "job-lock:" + jobName;
    }
}

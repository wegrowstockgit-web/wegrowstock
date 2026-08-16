package com.invsys.admin.security;

import com.invsys.core.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminLoginAttemptLimiterTest {

    @Test
    void locksAfterFiveFailuresAndResetsOnSuccess() {
        AdminLoginAttemptLimiter limiter = new AdminLoginAttemptLimiter(emptyRedis(), 5, 10);
        String ip = "198.51.100.20";
        String email = "ops@example.test";

        for (int i = 0; i < 5; i++) {
            limiter.assertAllowed(ip, email);
            limiter.recordFailure(ip, email);
        }
        assertThatThrownBy(() -> limiter.assertAllowed(ip, email))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.getCode()).isEqualTo(AdminLoginAttemptLimiter.RATE_LIMIT_CODE);
                });

        limiter.reset(ip, email);
        limiter.assertAllowed(ip, email);
    }

    private static ObjectProvider<StringRedisTemplate> emptyRedis() {
        return new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject() {
                return null;
            }

            @Override
            public StringRedisTemplate getIfAvailable() {
                return null;
            }

            @Override
            public StringRedisTemplate getIfUnique() {
                return null;
            }
        };
    }
}

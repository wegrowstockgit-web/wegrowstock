package com.invsys.idempotency;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keep {@link RedisIdempotencyFilter} out of the servlet container chain so it only
 * runs inside the Spring Security chain (after JWT / tenant binding).
 */
@Configuration
public class IdempotencyFilterRegistration {

    @Bean
    public FilterRegistrationBean<RedisIdempotencyFilter> redisIdempotencyFilterRegistration(
            RedisIdempotencyFilter filter) {
        FilterRegistrationBean<RedisIdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

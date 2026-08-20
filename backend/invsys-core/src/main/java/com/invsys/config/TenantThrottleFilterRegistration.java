package com.invsys.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keep {@link TenantThrottleFilter} out of the servlet container chain so it only
 * runs inside the Spring Security chain (after JWT / tenant binding).
 */
@Configuration
public class TenantThrottleFilterRegistration {

    @Bean
    public FilterRegistrationBean<TenantThrottleFilter> disableServletTenantThrottleFilter(
            TenantThrottleFilter filter) {
        FilterRegistrationBean<TenantThrottleFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

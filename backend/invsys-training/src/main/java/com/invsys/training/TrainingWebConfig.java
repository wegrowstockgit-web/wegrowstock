package com.invsys.training;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "invsys.features.training.enabled", havingValue = "true", matchIfMissing = true)
public class TrainingWebConfig implements WebMvcConfigurer {

    private final ShadowTenantInterceptor shadowTenantInterceptor;

    public TrainingWebConfig(ShadowTenantInterceptor shadowTenantInterceptor) {
        this.shadowTenantInterceptor = shadowTenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(shadowTenantInterceptor).addPathPatterns("/api/**");
    }
}

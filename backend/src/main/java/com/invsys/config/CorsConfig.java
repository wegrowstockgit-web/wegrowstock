package com.invsys.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${invsys.security.cors-allowed-origins}") String allowedOrigins,
            @Value("${invsys.frontend-url}") String frontendUrl) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (frontendUrl != null && !frontendUrl.isBlank() && !origins.contains(frontendUrl.trim())) {
            origins = new java.util.ArrayList<>(origins);
            origins.add(frontendUrl.trim());
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Idempotency-Key",
                "X-Request-Id",
                "Stripe-Signature",
                "X-Shopify-Hmac-Sha256",
                "X-Shopify-Shop-Domain",
                "X-Shopify-Topic",
                "X-Shopify-Webhook-Id",
                "X-Hmac-Signature"
        ));
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

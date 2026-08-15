package com.invsys.admin.security;

import com.invsys.core.security.UnauthorizedEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableMethodSecurity
public class AdminSecurityConfig {

    private final AdminJwtAuthFilter adminJwtAuthFilter;
    private final UnauthorizedEntryPoint unauthorizedEntryPoint;

    public AdminSecurityConfig(AdminJwtAuthFilter adminJwtAuthFilter,
                               UnauthorizedEntryPoint unauthorizedEntryPoint) {
        this.adminJwtAuthFilter = adminJwtAuthFilter;
        this.unauthorizedEntryPoint = unauthorizedEntryPoint;
    }

    @Bean
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");
        csrfRepo.setHeaderName("X-XSRF-TOKEN");

        // SPA-friendly: expose the raw token to the XSRF-TOKEN cookie (not XOR-masked).
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(
                                "/api/v1/control-plane/auth/login",
                                "/actuator/health",
                                "/actuator/health/**"))
                .cors(cors -> cors.disable())
                .headers(headers -> headers
                        .contentTypeOptions(c -> {
                        })
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/control-plane/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/control-plane/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/control-plane/**").hasRole("SUPER_ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(adminJwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Forces the CsrfFilter to write the XSRF-TOKEN cookie on the first request
     * (deferred token repositories only set the cookie when the token is accessed).
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrf != null) {
                csrf.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}

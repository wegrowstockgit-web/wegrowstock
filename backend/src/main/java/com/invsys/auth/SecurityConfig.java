package com.invsys.auth;

import com.invsys.auth.oidc.OidcLoginSuccessHandler;
import com.invsys.auth.oidc.TenantClientRegistrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final WarehouseAccessFilter warehouseAccessFilter;
    private final UnauthorizedEntryPoint unauthorizedEntryPoint;
    private final TenantClientRegistrationRepository tenantClientRegistrationRepository;
    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;
    private final Environment environment;
    private final boolean publicSignupEnabled;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          WarehouseAccessFilter warehouseAccessFilter,
                          UnauthorizedEntryPoint unauthorizedEntryPoint,
                          TenantClientRegistrationRepository tenantClientRegistrationRepository,
                          OidcLoginSuccessHandler oidcLoginSuccessHandler,
                          Environment environment,
                          @Value("${invsys.security.public-signup-enabled:true}") boolean publicSignupEnabled) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.warehouseAccessFilter = warehouseAccessFilter;
        this.unauthorizedEntryPoint = unauthorizedEntryPoint;
        this.tenantClientRegistrationRepository = tenantClientRegistrationRepository;
        this.oidcLoginSuccessHandler = oidcLoginSuccessHandler;
        this.environment = environment;
        this.publicSignupEnabled = publicSignupEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean prod = ArraysContainsProd(environment.getActiveProfiles());
        http.csrf(AbstractHttpConfigurer::disable)
                // CORS is enforced exclusively by com.invsys.gateway.ApiGatewayCorsFilter
                .cors(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(c -> {
                        })
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(p -> p.policy("geolocation=(self), microphone=(), camera=()")))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint))
                .authorizeHttpRequests(auth -> {
                    if (publicSignupEnabled) {
                        auth.requestMatchers("/api/v1/auth/signup").permitAll();
                    }
                    auth.requestMatchers("/api/v1/auth/login", "/api/v1/auth/warehouse/login",
                                    "/api/v1/auth/refresh",
                                    "/api/v1/auth/magic-login", "/api/v1/auth/magic-login/consume",
                                    "/api/v1/auth/sso-discover",
                                    "/api/v1/auth/sso-providers").permitAll()
                            .requestMatchers("/api/v1/invitations/accept").permitAll()
                            .requestMatchers("/api/v1/webhooks/**").permitAll()
                            .requestMatchers("/api/v1/public/**").permitAll()
                            .requestMatchers("/oauth2/**", "/login/oauth2/**", "/saml2/**").permitAll()
                            .requestMatchers("/actuator/health").permitAll()
                            // Scraped only on the Docker network; api-gateway blocks this path publicly.
                            .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                            .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll();
                    if (!prod) {
                        auth.requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.requestMatchers("/actuator/**").hasAnyRole("OWNER", "ADMIN")
                            .anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .clientRegistrationRepository(tenantClientRegistrationRepository)
                        .successHandler(oidcLoginSuccessHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(warehouseAccessFilter, JwtAuthFilter.class);
        return http.build();
    }

    private static boolean ArraysContainsProd(String[] profiles) {
        for (String profile : profiles) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}

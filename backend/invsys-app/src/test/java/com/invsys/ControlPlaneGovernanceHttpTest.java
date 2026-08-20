package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.service.FeatureFlagService;
import com.invsys.ratelimit.DistributedRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ControlPlaneGovernanceHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired DistributedRateLimiter distributedRateLimiter;
    @Autowired FeatureFlagService featureFlagService;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @AfterEach
    void tearDown() {
        distributedRateLimiter.resetLocal();
        featureFlagService.resetLocal();
    }

    @Test
    void killSwitchReturns429AndCustomRpsOverridesCapacity() throws Exception {
        String slug = "thr-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Throttle Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        distributedRateLimiter.setTenantThrottle(owner.tenantId(), true, null);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("TRAFFIC_PAUSED"));

        distributedRateLimiter.setTenantThrottle(owner.tenantId(), false, 1);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("RATE_LIMITED"));
    }

    @Test
    void tenantFeatureFlagsHonorGlobalAndOverride() throws Exception {
        String slug = "flg-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Flag Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        JdbcTemplate jdbc = new JdbcTemplate(bootstrapDataSource);
        UUID globalId = UUID.randomUUID();
        UUID betaId = UUID.randomUUID();
        String globalKey = "global-wave-" + globalId.toString().substring(0, 8);
        String betaKey = "beta-wave-" + betaId.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO feature_flags (id, flag_key, description, is_global, created_at)
                VALUES (?, ?, 'g', TRUE, NOW()), (?, ?, 'b', FALSE, NOW())
                """, globalId, globalKey, betaId, betaKey);
        jdbc.update("""
                INSERT INTO tenant_feature_flags (tenant_id, flag_id, enabled)
                VALUES (?, ?, TRUE)
                """, owner.tenantId(), betaId);

        assertThat(featureFlagService.isEnabled(globalKey, owner.tenantId())).isTrue();
        assertThat(featureFlagService.isEnabled(betaKey, owner.tenantId())).isTrue();
        assertThat(featureFlagService.isEnabled("missing-wave", owner.tenantId())).isFalse();

        mockMvc.perform(get("/api/v1/feature-flags")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags").isArray())
                .andExpect(jsonPath("$.flags[?(@=='" + globalKey + "')]").exists())
                .andExpect(jsonPath("$.flags[?(@=='" + betaKey + "')]").exists());
    }
}

package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.service.PredictiveReplenishmentWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BusinessAutomationSettingsHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantSettingsRepository tenantSettingsRepository;
    @Autowired PredictiveReplenishmentWorker predictiveReplenishmentWorker;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void patchPersistsAutomationFieldsAndGatesPredictiveWorker() throws Exception {
        String slug = "auto-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Auto Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictive_replenishment_enabled").value(true))
                .andExpect(jsonPath("$.blind_cycle_counts").value(true))
                .andExpect(jsonPath("$.max_auto_adjust_value").exists())
                .andExpect(jsonPath("$.rma_auto_approve_max_value").exists());

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "blind_cycle_counts": false,
                                  "predictive_replenishment_enabled": false,
                                  "max_auto_adjust_value": 55.5,
                                  "rma_auto_approve_max_value": 75.25
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blind_cycle_counts").value(false))
                .andExpect(jsonPath("$.predictive_replenishment_enabled").value(false))
                .andExpect(jsonPath("$.max_auto_adjust_value").value(55.5))
                .andExpect(jsonPath("$.rma_auto_approve_max_value").value(75.25));

        TenantContext.setTenantId(owner.tenantId());
        TenantSettings settings = tenantSettingsRepository.findByTenantId(owner.tenantId()).orElseThrow();
        assertThat(settings.isPredictiveReplenishmentEnabled()).isFalse();
        assertThat(settings.isBlindCycleCounts()).isFalse();

        assertThat(predictiveReplenishmentWorker.evaluateTenant(owner.tenantId())).isZero();
    }
}

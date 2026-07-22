package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.service.TenantSettingsCacheService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TenantSettingsCacheHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantSettingsCacheService cacheService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void patchSettingsInvalidatesCache_andFlushReloads() throws Exception {
        String slug = "ops-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Ops Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"picking_wave_max_lines":55,"over_receipt_tolerance_percent":5,"allow_over_receiving":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picking_wave_max_lines").value(55));

        TenantContext.setTenantId(owner.tenantId());
        // Patch + @PostUpdate leave the cache cold; plant a stale entry to prove flush reloads DB.
        assertThat(cacheService.get(owner.tenantId())).isEmpty();
        cacheService.put(owner.tenantId(), Map.of("stale", true));
        assertThat(cacheService.get(owner.tenantId()).orElseThrow()).containsEntry("stale", true);

        mockMvc.perform(post("/api/v1/settings/cache/flush")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picking_wave_max_lines").value(55));

        assertThat(cacheService.get(owner.tenantId()).orElseThrow())
                .doesNotContainKey("stale")
                .containsEntry("picking_wave_max_lines", 55);

        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allow_over_receiving").value(true));
    }
}

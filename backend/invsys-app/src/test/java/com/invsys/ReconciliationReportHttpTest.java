package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReconciliationReportHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired IntegrationSyncLogRepository syncLogRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reconciliationReturnsOkWhenFailedSyncLogHasNullEntityId() throws Exception {
        String slug = "recon-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Recon Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        TenantContext.setTenantId(owner.tenantId());
        IntegrationSyncLog failed = new IntegrationSyncLog();
        failed.setTenantId(owner.tenantId());
        failed.setSystem("AMAZON");
        failed.setEntityType("ORDER");
        failed.setEntityId(null);
        failed.setStatus("FAILED");
        failed.setLastError("No adapter registered for channel: AMAZON");
        syncLogRepository.save(failed);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/reports/reconciliation")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.physicalInventoryValue").exists())
                .andExpect(jsonPath("$.syncDrifts[0].system").value("AMAZON"))
                .andExpect(jsonPath("$.syncDrifts[0].entityId").value("n/a"))
                .andExpect(jsonPath("$.syncDrifts[0].message").value("No adapter registered for channel: AMAZON"));
    }
}

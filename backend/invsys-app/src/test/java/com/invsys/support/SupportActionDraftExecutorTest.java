package com.invsys.support;

import com.invsys.support.dto.ActionDraft;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.invsys.modules.catalog.domain.Location;

@ExtendWith(MockitoExtension.class)
class SupportActionDraftExecutorTest {

    @Mock SupportAgentTools agentTools;

    SupportActionDraftExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SupportActionDraftExecutor(agentTools);
        TenantContext.clear();
        TenantContext.setTenantId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executesAllowListedSupportActionViaAgentTools() {
        when(agentTools.execute(eq("generateCycleCount"), eq(Map.of("zoneId", "Aisle-4"))))
                .thenReturn(Map.of("ok", true, "cycleCountId", "cc-9"));

        Map<String, Object> result = executor.execute(new ActionDraft(
                "Generate cycle count for Aisle-4",
                "Creates a worksheet",
                "/api/v1/cycle-counts",
                Map.of("supportAction", "generateCycleCount", "zoneId", "Aisle-4")));

        assertThat(result).containsEntry("ok", true);
        assertThat(result).containsEntry("cycleCountId", "cc-9");
        assertThat(result).containsEntry("title", "Generate cycle count for Aisle-4");
        verify(agentTools).execute("generateCycleCount", Map.of("zoneId", "Aisle-4"));
    }

    @Test
    void rejectsUnknownEndpointWithoutSupportAction() {
        Map<String, Object> result = executor.execute(new ActionDraft(
                "Dangerous",
                "Would wipe data",
                "/api/v1/admin/wipe",
                Map.of("foo", "bar")));

        assertThat(result).containsEntry("ok", false);
        assertThat(result.get("error").toString()).containsIgnoringCase("approved list");
    }

    @Test
    void allowListedEndpointReturnsNavigationalApproval() {
        Map<String, Object> result = executor.execute(new ActionDraft(
                "Allocate order",
                "Reserve stock",
                "/api/v1/sales-orders/SO-1/allocate",
                Map.of()));

        assertThat(result).containsEntry("ok", true);
        assertThat(result).containsEntry("navigational", true);
        assertThat(result).containsEntry("targetEndpoint", "/api/v1/sales-orders/SO-1/allocate");
    }

    @Test
    void missingDraftFailsSoftly() {
        assertThat(executor.execute(null)).containsEntry("ok", false);
    }

    @Test
    void isAllowedEndpointAcceptsSafeMutationsOnly() {
        assertThat(SupportActionDraftExecutor.isAllowedEndpoint("/api/v1/picking/waves/1/release")).isTrue();
        assertThat(SupportActionDraftExecutor.isAllowedEndpoint("/api/v1/cycle-counts")).isTrue();
        assertThat(SupportActionDraftExecutor.isAllowedEndpoint("https://evil.example/api/v1/release")).isFalse();
        assertThat(SupportActionDraftExecutor.isAllowedEndpoint("/api/v1/products")).isFalse();
    }

    @Test
    void agentToolFailuresReturnSoftFailPayload() {
        when(agentTools.execute(eq("generateCycleCount"), eq(Map.of("zoneId", "Missing"))))
                .thenThrow(new RuntimeException("Location not found for barcode: Missing"));

        Map<String, Object> result = executor.execute(new ActionDraft(
                "Generate cycle count",
                "Creates a worksheet",
                "/api/v1/cycle-counts",
                Map.of("supportAction", "generateCycleCount", "zoneId", "Missing")));

        assertThat(result).containsEntry("ok", false);
        assertThat(result.get("error").toString()).containsIgnoringCase("Location not found");
    }
}

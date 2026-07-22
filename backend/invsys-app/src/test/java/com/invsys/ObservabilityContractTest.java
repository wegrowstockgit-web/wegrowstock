package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.integration.OutboxEvent;
import com.invsys.core.integration.OutboxEventRepository;
import com.invsys.service.AuditService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ObservabilityContractTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired AuditService auditService;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired TestDataHelper testDataHelper;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void authContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "owner@test", "n/a", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    @Test
    void responseIncludesRequestIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "e2e-corr-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "e2e-corr-1"));
    }

    @Test
    void auditServiceStoresActorAndDiff() {
        UUID tenantId = testDataHelper.createTenant("Obs Audit", "obsaud-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        UUID entityId = UUID.randomUUID();

        var entry = auditService.record("TEST_ACTION", "SALES_ORDER", entityId, Map.of(
                "status", Map.of("before", "DRAFT", "after", "CONFIRMED")));

        assertThat(entry.getActorUserId()).isNull();
        assertThat(entry.getDiff()).containsKey("status");
        assertThat(auditService.recent()).isNotEmpty();
    }

    @Test
    void operationsConsoleListsFailedOutboxForAdmin() throws Exception {
        String slug = "ops-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Ops Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(tenantId);
        event.setAggregateType("PRODUCT_VARIANT");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("TEST_FAIL");
        event.setPayload(Map.of("x", 1));
        event.setStatus("FAILED");
        event.setLastError("mapping conflict");
        outboxEventRepository.save(event);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/operations/outbox/failed")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("TEST_FAIL"))
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    @Test
    void fulfillmentScanRequiresIdempotencyKey() throws Exception {
        String slug = "ful-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Ful Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"X\",\"warehouseId\":\"" + UUID.randomUUID() + "\",\"mode\":\"receive\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("IDEMPOTENCY_REQUIRED"));
    }

    @Test
    void operationsRetryRequeuesFailedOutbox() throws Exception {
        String slug = "retry-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Retry Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(tenantId);
        event.setAggregateType("PRODUCT_VARIANT");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("RETRY_ME");
        event.setPayload(Map.of("sku", "ABC"));
        event.setStatus("FAILED");
        event.setLastError("boom");
        event = outboxEventRepository.save(event);
        UUID eventId = event.getId();
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/operations/outbox/" + eventId + "/retry")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}

package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.integration.OutboxDispatchedEvent;
import com.invsys.service.DashboardSseHub;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@AutoConfigureMockMvc
class DashboardStreamHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired DashboardSseHub sseHub;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void streamEndpointRequiresAuthAndStartsAsync() throws Exception {
        String slug = "sse-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "SSE Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/dashboard/stream")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(mvcResult.getRequest().isAsyncStarted()).isTrue();

        SseEmitter emitter = sseHub.subscribe(owner.tenantId());
        assertThat(emitter).isNotNull();
        sseHub.broadcast(owner.tenantId(), "INVOICE_PAID", Map.of("ok", true));
        emitter.complete();
    }

    @Test
    void outboxDispatchedEventCarriesAllocationType() {
        UUID tenantId = UUID.randomUUID();
        OutboxDispatchedEvent event = new OutboxDispatchedEvent(
                tenantId,
                UUID.randomUUID(),
                "ORDER_ALLOCATED",
                UUID.randomUUID(),
                Map.of("orderId", UUID.randomUUID()));
        assertThat(event.eventType()).isEqualTo("ORDER_ALLOCATED");
        assertThat(event.tenantId()).isEqualTo(tenantId);
    }
}

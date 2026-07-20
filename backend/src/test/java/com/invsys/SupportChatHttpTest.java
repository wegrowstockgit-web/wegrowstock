package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Location;
import com.invsys.repository.LocationRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SupportChatHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void streamsRoleAwareAnswerWithContextHeaders() throws Exception {
        String slug = "sup-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-User-Roles", "PICKER")
                        .header("X-Current-Route", "/fulfillment")
                        .content("{\"message\":\"How do I process an inbound shipment?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("event:token");
        assertThat(body.toLowerCase()).contains("scan");
        assertThat(body.toLowerCase()).doesNotContain("create po on the desktop");
    }

    @Test
    void managerCycleCountStreamsActionButtonEvent() throws Exception {
        String slug = "sup-act-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Actions", slug, "mgr@" + slug + ".test", "password123", "Owner"));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-User-Roles", "WAREHOUSE_MANAGER")
                        .header("X-Current-Route", "/cycle-counts")
                        .content("{\"message\":\"Please generate a cycle count for zone Aisle-4\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("event:action");
        assertThat(body).contains("action_button");
        assertThat(body).contains("generateCycleCount");
        assertThat(body).contains("Aisle-4");
        assertThat(body).contains("event:done");
        assertThat(body).contains("replyMarkdown");
        assertThat(body).contains("followUpQuestions");
        assertThat(body).contains("actionChips");
    }

    @Test
    void streamsStructuredInstructorPayloadWithPageState() throws Exception {
        String slug = "sup-struct-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Struct", slug, "mgr@" + slug + ".test", "password123", "Owner"));

        String content = """
                {
                  "message": "Why is this order BACKORDERED and how do I Un-allocate?",
                  "routeContext": { "pathname": "/sales-orders", "search": "?status=BACKORDERED" },
                  "pageState": {
                    "routePath": "/sales-orders?status=BACKORDERED",
                    "activeFilter": "status=BACKORDERED",
                    "selectedEntity": "SO-2026-0012",
                    "networkState": "online",
                    "userRoles": ["WAREHOUSE_MANAGER"]
                  },
                  "userRoles": ["WAREHOUSE_MANAGER"],
                  "pageContext": {
                    "title": "Sales Orders",
                    "purpose": "Allocate demand",
                    "reversals": ["Un-allocate releases ACTIVE allocations."]
                  }
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-User-Roles", "WAREHOUSE_MANAGER")
                        .header("X-Current-Route", "/sales-orders?status=BACKORDERED")
                        .content(content))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("event:token");
        assertThat(body).contains("event:done");
        assertThat(body).contains("replyMarkdown");
        assertThat(body).contains("followUpQuestions");
        assertThat(body).containsIgnoringCase("Diagnosis");
        assertThat(body).contains("NAVIGATE");
    }

    @Test
    void executeUnknownActionReturnsSoftFailure() throws Exception {
        String slug = "sup-ex-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Exec", slug, "owner@" + slug + ".test", "password123", "Owner"));

        String body = mockMvc.perform(post("/api/v1/support/actions/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .content("{\"action\":\"deleteWarehouse\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"ok\":false");
        assertThat(body).contains("UNKNOWN_ACTION");
    }

    @Test
    void executeGenerateCycleCountStartsCountForResolvedLocation() throws Exception {
        String slug = "sup-cc-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support CC", slug, "owner@" + slug + ".test", "password123", "Owner"));

        TenantContext.setTenantId(tokens.tenantId());
        Location bin = new Location();
        bin.setTenantId(tokens.tenantId());
        bin.setType("BIN");
        bin.setCode("Aisle-4");
        bin.setName("Aisle 4");
        bin.setPath("WH/Aisle-4");
        bin = locationRepository.save(bin);
        TenantContext.clear();

        String body = mockMvc.perform(post("/api/v1/support/actions/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .content("{\"action\":\"generateCycleCount\",\"params\":{\"zoneId\":\"Aisle-4\"}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"ok\":true");
        assertThat(body).contains("generateCycleCount");
        assertThat(body).contains("cycleCountId");
        assertThat(body).contains(bin.getId().toString());
    }
}

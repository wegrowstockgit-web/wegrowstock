package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.core.tenancy.TenantContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void multimodalChatAcceptsBase64ImageAliasAndReturnsVisionGuidance() throws Exception {
        String slug = "sup-vision-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Vision", slug, "picker@" + slug + ".test", "password123", "Owner"));

        // Minimal JPEG SOI/EOI markers — enough for Base64 decode + heuristic vision coach.
        String tinyJpeg = java.util.Base64.getEncoder().encodeToString(new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9
        });
        String content = """
                {
                  "message": "This shipping label is mangled — what should I scan next?",
                  "base64Image": "%s",
                  "imageMimeType": "image/jpeg",
                  "userRoles": ["PICKER"],
                  "pageState": { "pathname": "/inbound/receive" }
                }
                """.formatted(tinyJpeg);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-User-Roles", "PICKER")
                        .header("X-Current-Route", "/inbound/receive")
                        .content(content))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("event:done");
        assertThat(body.toLowerCase()).contains("photo");
        assertThat(body).doesNotContain("SupportChatService");
    }

    @Test
    void insightsEndpointReturnsProactiveInsightField() throws Exception {
        String slug = "sup-ins-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Insights", slug, "mgr@" + slug + ".test", "password123", "Owner"));

        String body = mockMvc.perform(get("/api/v1/support/insights")
                        .param("route", "/sales-orders")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Current-Route", "/sales-orders"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"ok\":true");
        assertThat(body).contains("proactiveInsight");
    }

    @Test
    void draftExecuteRunsAllowListedSupportAction() throws Exception {
        String slug = "sup-draft-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Draft", slug, "owner@" + slug + ".test", "password123", "Owner"));

        TenantContext.setTenantId(tokens.tenantId());
        Location bin = new Location();
        bin.setTenantId(tokens.tenantId());
        bin.setType("BIN");
        bin.setCode("Aisle-4");
        bin.setName("Aisle 4");
        bin.setPath("WH/Aisle-4");
        locationRepository.save(bin);
        TenantContext.clear();

        String content = """
                {
                  "actionDraft": {
                    "title": "Generate cycle count for Aisle-4",
                    "description": "Creates a worksheet",
                    "targetEndpoint": "/api/v1/cycle-counts",
                    "httpMethod": "POST",
                    "payload": {
                      "supportAction": "generateCycleCount",
                      "zoneId": "Aisle-4"
                    }
                  }
                }
                """;

        String body = mockMvc.perform(post("/api/v1/support/actions/draft-execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .content(content))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"ok\":true");
        assertThat(body).contains("cycleCountId");
        assertThat(body).contains("Generate cycle count for Aisle-4");
    }

    @Test
    void unallocateQuestionStreamsActionDraftInDonePayload() throws Exception {
        String slug = "sup-ua-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Support Unalloc", slug, "mgr@" + slug + ".test", "password123", "Owner"));

        String content = """
                {
                  "message": "Please unallocate reserved stock on this order",
                  "pageState": {
                    "pathname": "/sales-orders",
                    "selectedEntityId": "SO-2026-00030",
                    "userRoles": ["WAREHOUSE_MANAGER"]
                  },
                  "userRoles": ["WAREHOUSE_MANAGER"]
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-User-Roles", "WAREHOUSE_MANAGER")
                        .header("X-Current-Route", "/sales-orders")
                        .content(content))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("event:done");
        assertThat(body).contains("actionDraft");
        assertThat(body).containsIgnoringCase("Un-allocate");
        assertThat(body).contains("httpMethod");
        assertThat(body).contains("/allocate");
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

package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditQueryHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void entityAndTenantEndpointsReturnEnrichedCursorPages() throws Exception {
        String slug = "aq-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Audit Query Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"picking_wave_max_lines":42}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trail@%s.test","role":"VIEWER"}
                                """.formatted(slug)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit/entity/USER/" + owner.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Owner user row also produces USERS trigger events during signup/updates.
        MvcResult entity = mockMvc.perform(get("/api/v1/audit/entity/USERS/" + owner.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").exists())
                .andReturn();
        JsonNode entityRows = objectMapper.readTree(entity.getResponse().getContentAsString());
        assertThat(entityRows.isArray()).isTrue();
        if (!entityRows.isEmpty()) {
            assertThat(entityRows.get(0).path("actorEmail").asString()).isEqualTo("owner@" + slug + ".test");
        }

        MvcResult page1 = mockMvc.perform(get("/api/v1/audit/tenant")
                        .param("limit", "2")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        JsonNode page = objectMapper.readTree(page1.getResponse().getContentAsString());
        String cursor = page.path("nextCursor").asString();
        assertThat(cursor).isNotBlank();

        mockMvc.perform(get("/api/v1/audit/tenant")
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .param("entityType", "TENANT_SETTINGS")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/api/v1/audit/tenant")
                        .param("action", "INVITE_USER")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("INVITE_USER"))
                .andExpect(jsonPath("$.items[0].actorDisplayName").isNotEmpty());
    }

    @Test
    void pickerForbiddenFromAuditApis() throws Exception {
        String slug = "aqb-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Audit Block", slug, "owner@" + slug + ".test", "password123", "Owner"));
        MvcResult invite = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","role":"PICKER"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(invite.getResponse().getContentAsString())
                .get("token").asString();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Picker","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());
        TokenResponse picker = authService.login(new com.invsys.core.security.dto.LoginRequest(
                "picker@" + slug + ".test", "password123"));

        mockMvc.perform(get("/api/v1/audit/tenant")
                        .header("Authorization", "Bearer " + picker.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/entity/USER/" + owner.userId())
                        .header("Authorization", "Bearer " + picker.accessToken()))
                .andExpect(status().isForbidden());
    }
}

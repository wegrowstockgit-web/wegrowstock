package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.AuditLog;
import com.invsys.repository.AuditLogRepository;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserInviteHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void inviteCreatesPendingRow_listsAndBlocksDuplicate_audits() throws Exception {
        String slug = "inv-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Invite Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        String email = "newbie@" + slug + ".test";

        MvcResult created = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"PICKER"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PICKER"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String inviteId = body.get("id").asString();

        mockMvc.perform(get("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(inviteId))
                .andExpect(jsonPath("$[0].email").value(email))
                .andExpect(jsonPath("$[0].role").value("PICKER"));

        mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"VIEWER"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("INVITE_PENDING"));

        TenantContext.setTenantId(tenantId);
        assertThat(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .extracting(AuditLog::getAction)
                .contains("USER_INVITE");
    }

    @Test
    void pickerForbiddenFromInviteEndpoint() throws Exception {
        String slug = "invb-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Invite Block", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult inviteMvc = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","role":"PICKER"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(inviteMvc.getResponse().getContentAsString())
                .get("token").asString();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Floor Picker","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());

        TokenResponse picker = authService.login(new com.invsys.core.security.dto.LoginRequest(
                "picker@" + slug + ".test", "password123"));

        mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + picker.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"other@%s.test","role":"VIEWER"}
                                """.formatted(slug)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + picker.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resendExtendsExpiry_dispatchesAndAudits() throws Exception {
        String slug = "rsnd-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Resend Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        String email = "remind@" + slug + ".test";

        MvcResult created = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"VIEWER"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode invite = objectMapper.readTree(created.getResponse().getContentAsString());
        String inviteId = invite.get("id").asString();
        Instant originalExpiry = Instant.parse(invite.get("expiresAt").asString());

        Thread.sleep(20);

        MvcResult resent = mockMvc.perform(post("/api/v1/office/invitations/" + inviteId + "/resend")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailDispatched").value(true))
                .andExpect(jsonPath("$.inviteUrl").isNotEmpty())
                .andReturn();
        Instant newExpiry = Instant.parse(
                objectMapper.readTree(resent.getResponse().getContentAsString()).get("expiresAt").asString());
        assertThat(newExpiry).isAfter(originalExpiry.minusSeconds(1));
        assertThat(newExpiry).isAfter(Instant.now().plusSeconds(6 * 86400));

        mockMvc.perform(get("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(inviteId));

        TenantContext.setTenantId(tenantId);
        assertThat(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .extracting(AuditLog::getAction)
                .contains("RESEND_INVITATION", "USER_INVITE");
        AuditLog resendAudit = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(a -> "RESEND_INVITATION".equals(a.getAction()))
                .filter(a -> a.getDiff() != null && a.getDiff().containsKey("extendedExpiresAt"))
                .findFirst()
                .orElseThrow();
        assertThat(resendAudit.getEntityType()).isEqualTo("INVITATION");
        assertThat(resendAudit.getEntityId().toString()).isEqualTo(inviteId);
        assertThat(resendAudit.getActorUserId()).isEqualTo(owner.userId());
        assertThat(resendAudit.getDiff()).containsKeys("extendedExpiresAt", "targetEmail");

        assertThat(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .anyMatch(a -> "RESEND_INVITATION".equals(a.getAction())
                        && "spring_aop".equals(String.valueOf(a.getDiff().get("source"))));
    }
}

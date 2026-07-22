package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.AuditLog;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.User;
import com.invsys.repository.AuditLogRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserWarehouseRepository;
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
class UserManagementTwoTierTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserWarehouseRepository userWarehouseRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void selfServiceCannotSetOrgFieldsViaProfile_adminOrgScopeAudited() throws Exception {
        String slug = "tier-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Tier Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-T1");
        wh.setName("Tier WH");
        wh.setPath("WH-T1");
        wh = locationRepository.save(wh);

        // Invite + accept a picker
        MvcResult inviteMvc = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","role":"PICKER"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode inviteJson = objectMapper.readTree(inviteMvc.getResponse().getContentAsString());
        String token = inviteJson.get("token").asString();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Picker One","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());

        TokenResponse picker = authService.login(new com.invsys.core.security.dto.LoginRequest(
                "picker@" + slug + ".test", "password123"));

        // Picker self-service personal fields OK
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + picker.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "555-9999",
                                  "addressCity": "Dallas",
                                  "addressCountry": "US",
                                  "mfaEnabled": true,
                                  "uiDensityPreference": "COMFORTABLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("555-9999"))
                .andExpect(jsonPath("$.addressCity").value("Dallas"));

        // Picker cannot hit admin org-scope
        mockMvc.perform(patch("/api/v1/users/" + picker.userId() + "/org-scope")
                        .header("Authorization", "Bearer " + picker.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"corporateDepartment":"Hack","timezonePreference":"UTC"}
                                """))
                .andExpect(status().isForbidden());

        // Owner updates org scope + warehouses → audit
        mockMvc.perform(patch("/api/v1/users/" + picker.userId() + "/org-scope")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "corporateDepartment": "Outbound",
                                  "timezonePreference": "America/Chicago",
                                  "localeLanguage": "en-US",
                                  "shiftScheduleType": "NIGHT",
                                  "assignedWarehouseId": "%s",
                                  "warehouseIds": ["%s"]
                                }
                                """.formatted(wh.getId(), wh.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateDepartment").value("Outbound"))
                .andExpect(jsonPath("$.shiftScheduleType").value("NIGHT"))
                .andExpect(jsonPath("$.warehouseIds[0]").value(wh.getId().toString()));

        TenantContext.setTenantId(tenantId);
        User refreshed = userRepository.findById(picker.userId()).orElseThrow();
        assertThat(refreshed.getCorporateDepartment()).isEqualTo("Outbound");
        assertThat(refreshed.getShiftScheduleType()).isEqualTo("NIGHT");
        assertThat(userWarehouseRepository.findByTenantIdAndUserId(tenantId, picker.userId())).hasSize(1);

        assertThat(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .extracting(AuditLog::getAction)
                .contains("USER_ORG_UPDATE");
        AuditLog entry = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(a -> "USER_ORG_UPDATE".equals(a.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(entry.getEntityId()).isEqualTo(picker.userId());
        assertThat(entry.getDiff().toString()).contains("timezonePreference");

        // Password change
        mockMvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + picker.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"password456"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","password":"password456"}
                                """.formatted(slug)))
                .andExpect(status().isOk());
    }
}

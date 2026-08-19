package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.PermissionKeys;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CustomRoleProvisioningHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void customRoleCrud_systemRolesLocked() throws Exception {
        String slug = "roles-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Roles Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String bearer = "Bearer " + owner.accessToken();

        MvcResult listResult = mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roles = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode admin = findRole(roles, "ADMIN");
        JsonNode picker = findRole(roles, "PICKER");
        assertThat(admin.get("isSystemRole").asBoolean()).isTrue();
        assertThat(picker.get("isSystemRole").asBoolean()).isTrue();
        String adminId = admin.get("id").asText();
        String pickerId = picker.get("id").asText();

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Junior Buyer\",\"cloneFromRoleId\":\"" + pickerId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("JUNIOR_BUYER"))
                .andExpect(jsonPath("$.isSystemRole").value(false));

        MvcResult afterCreate = mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = findRole(objectMapper.readTree(afterCreate.getResponse().getContentAsString()),
                "JUNIOR_BUYER");
        String customId = created.get("id").asText();
        assertThat(created.get("isSystemRole").asBoolean()).isFalse();

        mockMvc.perform(patch("/api/v1/settings/permissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + adminId + "\",\"permissionKey\":\""
                                + PermissionKeys.INVENTORY_COST_VIEW + "\",\"granted\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("System roles cannot be modified."))
                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_LOCKED"));

        mockMvc.perform(delete("/api/v1/roles/" + adminId).header("Authorization", bearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("System roles cannot be modified."));

        mockMvc.perform(put("/api/v1/roles/" + customId + "/permissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grants\":[{\"permissionKey\":\""
                                + PermissionKeys.INVENTORY_COST_VIEW + "\",\"granted\":true}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/settings/permissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + customId + "\",\"permissionKey\":\""
                                + PermissionKeys.PRINTING_THERMAL + "\",\"granted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(true));

        mockMvc.perform(delete("/api/v1/roles/" + customId).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        MvcResult afterDelete = mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode remaining = objectMapper.readTree(afterDelete.getResponse().getContentAsString());
        assertThat(findRoleOrNull(remaining, "JUNIOR_BUYER")).isNull();
    }

    private static JsonNode findRole(JsonNode roles, String name) {
        JsonNode found = findRoleOrNull(roles, name);
        assertThat(found).as("role %s", name).isNotNull();
        return found;
    }

    private static JsonNode findRoleOrNull(JsonNode roles, String name) {
        for (JsonNode role : roles) {
            if (name.equals(role.get("name").asText())) {
                return role;
            }
        }
        return null;
    }
}

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WarehouseGeoAndRtlsHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void warehouseRequiresLatLng_andRtlsIngestPublishesFrame() throws Exception {
        String slug = "geo-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Geo Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(post("/api/v1/locations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"WAREHOUSE","code":"WHG","name":"Geo WH","path":"WHG",
                                 "logisticsAddress":{"city":"Austin","country":"US"}}
                                """))
                .andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/v1/locations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"WAREHOUSE","code":"WHG","name":"Geo WH","path":"WHG",
                                 "latitude":30.2672,"longitude":-97.7431,
                                 "logisticsAddress":{"city":"Austin","country":"US"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(30.2672))
                .andExpect(jsonPath("$.longitude").value(-97.7431))
                .andReturn();
        JsonNode wh = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(wh.get("id").asString()).isNotBlank();

        mockMvc.perform(put("/api/v1/rtls/tags")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagId":"TAG-1","technology":"UWB","assetType":"PALLET","label":"Pallet A"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagId").value("TAG-1"));

        mockMvc.perform(post("/api/v1/rtls/telemetry")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"packets":[{"tagId":"TAG-1","technology":"BLE_AOA","azimuth":45,"rangeM":10}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tagId").value("TAG-1"))
                .andExpect(jsonPath("$[0].x").isNumber())
                .andExpect(jsonPath("$[0].y").isNumber());

        mockMvc.perform(get("/api/v1/rtls/positions/recent")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].technology").value("BLE_AOA"));
    }

    @Test
    void ssoProvidersCatalogIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sso-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].id").value("GOOGLE"))
                .andExpect(jsonPath("$.providers[1].id").value("ENTRA"))
                .andExpect(jsonPath("$.providers[2].id").value("OKTA"));
    }
}

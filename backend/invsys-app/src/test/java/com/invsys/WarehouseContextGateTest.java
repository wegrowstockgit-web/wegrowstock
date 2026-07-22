package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.WarehouseAccessFilter;
import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.domain.UserWarehouse;
import com.invsys.domain.WarehouseContextRule;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.repository.UserWarehouseRepository;
import com.invsys.repository.WarehouseContextRuleRepository;
import com.invsys.service.WarehouseContextService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WarehouseContextGateTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired UserWarehouseRepository userWarehouseRepository;
    @Autowired WarehouseContextRuleRepository ruleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ssidResolveLocksToAuthorizedWarehouse() throws Exception {
        String slug = "ctx-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Context Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Location wh01 = locationRepository.findByTenantIdAndCode(tenantId, "WH-01").orElseThrow();
        Location wh02 = new Location();
        wh02.setTenantId(tenantId);
        wh02.setType("WAREHOUSE");
        wh02.setCode("WH-02");
        wh02.setName("Overflow");
        wh02.setPath("/WH-02");
        wh02 = locationRepository.save(wh02);

        WarehouseContextRule rule = new WarehouseContextRule();
        rule.setTenantId(tenantId);
        rule.setLocationId(wh01.getId());
        rule.setMatchType("WIFI_SSID");
        rule.setSsid("InvSys-Floor-A");
        rule.setPriority(10);
        rule.setEnabled(true);
        rule.setLabel("Floor A AP");
        ruleRepository.save(rule);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/terminals/resolve-context")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssid\":\"InvSys-Floor-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.matchType").value("WIFI_SSID"))
                .andExpect(jsonPath("$.warehouseId").value(wh01.getId().toString()));

        mockMvc.perform(post("/api/v1/terminals/resolve-context")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssid\":\"Unknown-Network\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false));

        assertThat(wh02.getId()).isNotEqualTo(wh01.getId());
    }

    @Test
    void geofenceResolveUsesHaversine() throws Exception {
        String slug = "geo-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Geo Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Location wh01 = locationRepository.findByTenantIdAndCode(tenantId, "WH-01").orElseThrow();

        WarehouseContextRule rule = new WarehouseContextRule();
        rule.setTenantId(tenantId);
        rule.setLocationId(wh01.getId());
        rule.setMatchType("GEOFENCE");
        rule.setLatitude(new BigDecimal("41.881832"));
        rule.setLongitude(new BigDecimal("-87.623177"));
        rule.setRadiusMeters(new BigDecimal("500"));
        rule.setPriority(20);
        rule.setEnabled(true);
        rule.setLabel("Chicago yard");
        ruleRepository.save(rule);
        TenantContext.clear();

        // ~120m from center â€” inside
        mockMvc.perform(post("/api/v1/terminals/resolve-context")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":41.8825,\"longitude\":-87.6232}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.matchType").value("GEOFENCE"))
                .andExpect(jsonPath("$.warehouseId").value(wh01.getId().toString()));

        // Far away â€” outside
        mockMvc.perform(post("/api/v1/terminals/resolve-context")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":40.0,\"longitude\":-74.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false));
    }

    @Test
    void pickerWithoutHeaderAutoLocksToSoleWarehouse() throws Exception {
        String slug = "auto-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse ownerTokens = authService.signup(new SignupRequest(
                "Auto Lock Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = ownerTokens.tenantId();

        TenantContext.setTenantId(tenantId);
        Location wh01 = locationRepository.findByTenantIdAndCode(tenantId, "WH-01").orElseThrow();

        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);

        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(picker.getId());
        userRole.setRoleId(pickerRole.getId());
        userRoleRepository.save(userRole);

        UserWarehouse mapping = new UserWarehouse();
        mapping.setTenantId(tenantId);
        mapping.setUserId(picker.getId());
        mapping.setLocationId(wh01.getId());
        userWarehouseRepository.save(mapping);
        TenantContext.clear();

        TokenResponse pickerTokens = authService.login(
                new LoginRequest("picker@" + slug + ".test", "password123"));
        assertThat(pickerTokens.warehouseIds()).containsExactly(wh01.getId());

        // No X-Warehouse-Id â€” filter should auto-apply sole warehouse (must not 403)
        mockMvc.perform(get("/api/v1/locations")
                        .param("type", "WAREHOUSE")
                        .header("Authorization", "Bearer " + pickerTokens.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void haversineDistanceIsAccurate() {
        double meters = WarehouseContextService.haversineMeters(41.881832, -87.623177, 41.8825, -87.6232);
        assertThat(meters).isCloseTo(75.0, within(50.0));
    }

    @Test
    void ownerCanCrudContextRulesAndPickerCannotEscapeLbacViaSsid() throws Exception {
        String slug = "crud-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "CRUD Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Location wh01 = locationRepository.findByTenantIdAndCode(tenantId, "WH-01").orElseThrow();
        Location wh02 = new Location();
        wh02.setTenantId(tenantId);
        wh02.setType("WAREHOUSE");
        wh02.setCode("WH-02");
        wh02.setName("Overflow");
        wh02.setPath("/WH-02");
        wh02 = locationRepository.save(wh02);

        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);
        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(picker.getId());
        userRole.setRoleId(pickerRole.getId());
        userRoleRepository.save(userRole);
        UserWarehouse mapping = new UserWarehouse();
        mapping.setTenantId(tenantId);
        mapping.setUserId(picker.getId());
        mapping.setLocationId(wh01.getId());
        userWarehouseRepository.save(mapping);
        TenantContext.clear();

        String createBody = """
                {"locationId":"%s","matchType":"WIFI_SSID","ssid":"Site-Overflow","priority":5,"enabled":true,"label":"Overflow SSID"}
                """.formatted(wh02.getId());
        String created = mockMvc.perform(post("/api/v1/warehouse-context-rules")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchType").value("WIFI_SSID"))
                .andReturn().getResponse().getContentAsString();
        String ruleId = created.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/warehouse-context-rules")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ssid").value("Site-Overflow"));

        String updateBody = """
                {"locationId":"%s","matchType":"GEOFENCE","latitude":41.88,"longitude":-87.62,"radiusMeters":250,"priority":1,"enabled":true,"label":"Yard"}
                """.formatted(wh01.getId());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/warehouse-context-rules/" + ruleId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchType").value("GEOFENCE"));

        // Re-create SSID rule on WH-02 â€” picker authorized only for WH-01 must not match
        mockMvc.perform(post("/api/v1/warehouse-context-rules")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        TokenResponse pickerTokens = authService.login(
                new LoginRequest("picker@" + slug + ".test", "password123"));
        mockMvc.perform(post("/api/v1/terminals/resolve-context")
                        .header("Authorization", "Bearer " + pickerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssid\":\"Site-Overflow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/warehouse-context-rules/" + ruleId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());
    }
}

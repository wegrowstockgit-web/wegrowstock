package com.invsys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EnterpriseMasterDataHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void warehouseCustomerSupplierAndProfileEnterpriseFieldsRoundTrip() throws Exception {
        String slug = "ent-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Ent Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult wh = mockMvc.perform(post("/api/v1/locations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "WAREHOUSE",
                                  "code": "WH-ENT",
                                  "name": "Enterprise DC",
                                  "path": "WH-ENT",
                                  "logisticsAddress": {
                                    "street": "100 Dock Way",
                                    "city": "Dallas",
                                    "state": "TX",
                                    "postalCode": "75201",
                                    "country": "US"
                                  },
                                  "grossSquareFootage": 250000,
                                  "officeAreaSquareFootage": 12000,
                                  "clearHeightFeet": 36,
                                  "totalDockDoors": 48,
                                  "weightCapacityLimit": 50000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearHeightFeet").value(36))
                .andExpect(jsonPath("$.totalDockDoors").value(48))
                .andExpect(jsonPath("$.logisticsAddress.city").value("Dallas"))
                .andReturn();

        JsonNode warehouse = objectMapper.readTree(wh.getResponse().getContentAsString());
        String warehouseId = warehouse.get("id").asText();

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme Retail",
                                  "email": "ap@acme.test",
                                  "taxId": "12-3456789",
                                  "paymentTerms": "NET30",
                                  "creditLimit": 25000,
                                  "currencyPreference": "USD",
                                  "customerStatus": "ACTIVE",
                                  "billingAddress": {"street": "1 Main", "city": "Austin", "state": "TX", "postalCode": "78701", "country": "US"},
                                  "shippingAddress": {"street": "2 Depot", "city": "Austin", "state": "TX", "postalCode": "78702", "country": "US"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentTerms").value("NET30"))
                .andExpect(jsonPath("$.creditLimit").value(25000))
                .andExpect(jsonPath("$.customerStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.billingAddress.city").value("Austin"));

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Global Parts",
                                  "contact": {"email": "buy@parts.test"},
                                  "paymentTerms": "NET60",
                                  "taxId": "98-7654321",
                                  "businessRegistration": "BR-99",
                                  "bankAccountIban": "GB82WEST12345698765432",
                                  "routingNumber": "021000021",
                                  "defaultLeadTimeDays": 14,
                                  "minimumOrderQuantityValue": 500,
                                  "supplierRating": 4.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentTerms").value("NET60"))
                .andExpect(jsonPath("$.defaultLeadTimeDays").value(14))
                .andExpect(jsonPath("$.supplierRating").value(4.5))
                .andExpect(jsonPath("$.bankAccountIban").value("****5432"))
                .andExpect(jsonPath("$.contactEmail").value("buy@parts.test"));

        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Owner Updated",
                                  "phone": "+1-555-0100",
                                  "addressCity": "Austin",
                                  "addressCountry": "US",
                                  "mfaEnabled": true,
                                  "uiDensityPreference": "COMPACT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Owner Updated"))
                .andExpect(jsonPath("$.mfaEnabled").value(true))
                .andExpect(jsonPath("$.phone").value("+1-555-0100"))
                .andExpect(jsonPath("$.uiDensityPreference").value("COMPACT"));

        mockMvc.perform(patch("/api/v1/users/" + owner.userId() + "/org-scope")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "corporateDepartment": "Outbound",
                                  "timezonePreference": "America/Chicago",
                                  "localeLanguage": "en-US",
                                  "assignedWarehouseId": "%s",
                                  "shiftScheduleType": "DAY"
                                }
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateDepartment").value("Outbound"))
                .andExpect(jsonPath("$.shiftScheduleType").value("DAY"))
                .andExpect(jsonPath("$.assignedWarehouseId").value(warehouseId));

        MvcResult me = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Outbound"))
                .andExpect(jsonPath("$.timezonePreference").value("America/Chicago"))
                .andExpect(jsonPath("$.phone").value("+1-555-0100"))
                .andReturn();

        assertThat(objectMapper.readTree(me.getResponse().getContentAsString())
                .get("assignedWarehouseId").asText()).isEqualTo(warehouseId);
    }
}

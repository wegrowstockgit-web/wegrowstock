package com.invsys.pos;

import com.invsys.pos.dto.PosSessionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PosSessionControllerTest {

    @Mock PosSessionService sessionService;
    @Mock PosManagerOverrideService managerOverrideService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PosSessionController(sessionService, managerOverrideService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    void session_returnsResolvedConfig() throws Exception {
        when(sessionService.currentSession(eq("es-MX"), eq("America/Mexico_City"), eq("es"), eq("MXN")))
                .thenReturn(new PosSessionResponse(
                        true, "RETAIL_POS", "ENTERPRISE", "es", "ORGANIZATION",
                        "MXN", "PLACE", "es", "MXN", "es-MX", "MX",
                        "America/Mexico_City", "Demo Corp",
                        java.util.UUID.fromString("a0000000-0000-4000-8000-000000000201"),
                        java.util.UUID.fromString("a0000000-0000-4000-8000-000000000001"),
                        "USD", java.math.BigDecimal.ONE));

        mockMvc.perform(get("/api/v1/pos/session")
                        .header("Accept-Language", "es-MX")
                        .param("timezone", "America/Mexico_City")
                        .param("placeLanguage", "es")
                        .param("placeCurrency", "MXN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posEnabled").value(true))
                .andExpect(jsonPath("$.language").value("es"))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.placeCurrency").value("MXN"))
                .andExpect(jsonPath("$.taxRegionHint").value("MX"))
                .andExpect(jsonPath("$.tenantBaseCurrency").value("USD"));
    }

    @Test
    void session_returnsLockedFlagWithoutModuleError() throws Exception {
        when(sessionService.currentSession(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PosSessionResponse(
                        false, "RETAIL_POS", "BASIC", "en", "DEFAULT",
                        "USD", "DEFAULT", null, "USD", "en-US", "US",
                        null, "",
                        java.util.UUID.fromString("a0000000-0000-4000-8000-000000000201"),
                        java.util.UUID.fromString("a0000000-0000-4000-8000-000000000001"),
                        "USD", java.math.BigDecimal.ONE));

        mockMvc.perform(get("/api/v1/pos/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posEnabled").value(false))
                .andExpect(jsonPath("$.tier").value("BASIC"));
    }

    @Test
    void syncManagerPins_returnsSupervisorHashes() throws Exception {
        java.util.UUID tenantId = java.util.UUID.fromString("a0000000-0000-4000-8000-000000000001");
        java.util.UUID managerId = java.util.UUID.fromString("a0000000-0000-4000-8000-000000000203");
        when(managerOverrideService.currentManagers()).thenReturn(
                new com.invsys.pos.dto.PosManagerOverrideResponse(
                        tenantId,
                        java.util.List.of(new com.invsys.pos.dto.PosManagerOverrideResponse.ManagerPin(managerId, "hash"))));

        mockMvc.perform(get("/api/v1/pos/managers/sync-pins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.managers[0].managerId").value(managerId.toString()));
    }
}

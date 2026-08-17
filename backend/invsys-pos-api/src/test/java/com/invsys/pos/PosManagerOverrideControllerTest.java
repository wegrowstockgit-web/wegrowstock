package com.invsys.pos;

import com.invsys.pos.dto.PosManagerOverrideResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PosManagerOverrideControllerTest {

    @Mock PosManagerOverrideService managerOverrideService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PosManagerOverrideController(managerOverrideService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    void managerOverrides_returnsCachedHashes() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(managerOverrideService.currentManagers()).thenReturn(new PosManagerOverrideResponse(
                tenantId, List.of(new PosManagerOverrideResponse.ManagerPin(managerId, "abc123"))));

        mockMvc.perform(get("/api/v1/pos/manager-overrides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.managers[0].managerId").value(managerId.toString()))
                .andExpect(jsonPath("$.managers[0].pinHash").value("abc123"));
    }
}

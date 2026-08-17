package com.invsys.pos;

import com.invsys.pos.dto.PosAuditEventDto;
import com.invsys.pos.dto.PosAuditSyncResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PosAuditSyncControllerTest {

    @Mock PosAuditSyncService auditSyncService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new PosAuditSyncController(auditSyncService))
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void syncAuditEvents_returnsProcessorResult() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(auditSyncService.sync(anyList())).thenReturn(new PosAuditSyncResponse(1, 0, List.of()));

        List<PosAuditEventDto> body = List.of(new PosAuditEventDto(
                eventId,
                1_700_000_000_000L,
                UUID.randomUUID().toString(),
                "LINE_VOID",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new BigDecimal("12.50"),
                null));

        mockMvc.perform(post("/api/v1/pos/audit-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(0));

        verify(auditSyncService).sync(anyList());
    }

    @Test
    void syncAuditEvents_partialRejectsStayHttp200() throws Exception {
        UUID rejectedId = UUID.randomUUID();
        when(auditSyncService.sync(anyList())).thenReturn(new PosAuditSyncResponse(
                0, 0, List.of(new PosAuditSyncResponse.RejectedEvent(rejectedId, "Unsupported POS exception type."))));

        mockMvc.perform(post("/api/v1/pos/audit-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","timestamp":1,"cashierId":"c","eventType":"UNKNOWN",
                                  "orderId":"%s","valueVoided":1.00}]
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected[0].reason").value("Unsupported POS exception type."));
    }
}

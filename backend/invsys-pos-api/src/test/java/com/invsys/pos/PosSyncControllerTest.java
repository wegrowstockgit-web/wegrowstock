package com.invsys.pos;

import com.invsys.pos.dto.OfflineReceiptDto;
import com.invsys.pos.dto.OfflineReceiptDto.OfflineReceiptLineDto;
import com.invsys.pos.dto.PosSyncResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PosSyncControllerTest {

    @Mock PosReceiptProcessor processor;
    @Mock PosSessionService sessionService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new PosSyncController(processor, sessionService))
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void syncReceipts_returnsProcessorResult() throws Exception {
        UUID receiptId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(processor.processReceipts(anyList())).thenReturn(new PosSyncResponse(1, 0, List.of()));

        List<OfflineReceiptDto> body = List.of(new OfflineReceiptDto(
                receiptId,
                storeId,
                List.of(new OfflineReceiptLineDto(variantId, "7501234567890", BigDecimal.ONE, new BigDecimal("2.00"))),
                "CASH",
                "US"));

        mockMvc.perform(post("/api/v1/pos/sync-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(0));

        verify(processor).processReceipts(anyList());
    }

    @Test
    void syncReceipts_partialRejectsStayHttp200() throws Exception {
        UUID rejectedId = UUID.randomUUID();
        when(processor.processReceipts(anyList())).thenReturn(new PosSyncResponse(
                0, 1, List.of(new PosSyncResponse.RejectedReceipt(rejectedId, "Store location was not found for this tenant."))));

        mockMvc.perform(post("/api/v1/pos/sync-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","storeLocationId":"%s","tenderType":"CARD","taxRegion":"MX",
                                  "lines":[{"upc":"1","quantity":1}]}]
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.rejected[0].reason").value("Store location was not found for this tenant."));
    }

    @Test
    void catalogSync_returnsMappedItems() throws Exception {
        UUID variantId = UUID.randomUUID();
        when(sessionService.syncCatalog()).thenReturn(List.of(new com.invsys.pos.dto.PosCatalogItem(
                variantId, "7700222200099", "POS-1", "POS Widget", new BigDecimal("4.50"), "/api/v1/media/x/content")));

        mockMvc.perform(get("/api/v1/pos/catalog-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variantId").value(variantId.toString()))
                .andExpect(jsonPath("$[0].upc").value("7700222200099"))
                .andExpect(jsonPath("$[0].sku").value("POS-1"))
                .andExpect(jsonPath("$[0].name").value("POS Widget"))
                .andExpect(jsonPath("$[0].retailPrice").value(4.50));
    }

    @Test
    void catalogLookup_returnsSingleItem() throws Exception {
        UUID variantId = UUID.randomUUID();
        when(sessionService.lookupByUpc("7700222200099")).thenReturn(new com.invsys.pos.dto.PosCatalogItem(
                variantId, "7700222200099", "POS-1", "POS Widget", new BigDecimal("4.50"), null));

        mockMvc.perform(get("/api/v1/pos/catalog/lookup").param("upc", "7700222200099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.upc").value("7700222200099"));
    }
}

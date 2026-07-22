package com.invsys.support;

import com.invsys.modules.fulfillment.domain.PickingWave;
import com.invsys.modules.inventory.service.CycleCountService;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.PickingWaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportAgentToolsTest {

    @Mock CycleCountService cycleCountService;
    @Mock PickingWaveService pickingWaveService;
    @Mock InventoryService inventoryService;

    SupportAgentTools tools;

    @BeforeEach
    void setUp() {
        tools = new SupportAgentTools(cycleCountService, pickingWaveService, inventoryService);
    }

    @Test
    void proposeGenerateCycleCountReturnsActionButtonJson() {
        String json = tools.proposeGenerateCycleCount("Aisle-4");
        assertThat(json).contains("\"type\":\"action_button\"");
        assertThat(json).contains("\"action\":\"generateCycleCount\"");
        assertThat(json).contains("\"zoneId\":\"Aisle-4\"");
        assertThat(json).contains("Generate cycle count for Aisle-4");
    }

    @Test
    void proposeReleaseWaveReturnsActionButtonJson() {
        UUID waveId = UUID.randomUUID();
        String json = tools.proposeReleaseWave(waveId.toString());
        assertThat(json).contains("\"type\":\"action_button\"");
        assertThat(json).contains("\"action\":\"releaseWave\"");
        assertThat(json).contains("\"waveId\":\"" + waveId + "\"");
    }

    @Test
    void executeGenerateCycleCountResolvesLocationAndStartsCount() {
        UUID locationId = UUID.randomUUID();
        UUID countId = UUID.randomUUID();
        when(inventoryService.resolveLocationId("Dock-1")).thenReturn(locationId);
        when(cycleCountService.startCount(locationId)).thenReturn(new CycleCountService.CycleCountDetail(
                countId,
                locationId,
                "WH/Dock-1",
                "IN_PROGRESS",
                "Manual cycle count",
                false,
                BigDecimal.ZERO,
                List.of()));

        Map<String, Object> result = tools.execute("generateCycleCount", Map.of("zoneId", "Dock-1"));

        assertThat(result).containsEntry("ok", true);
        assertThat(result).containsEntry("action", "generateCycleCount");
        assertThat(result).containsEntry("cycleCountId", countId.toString());
        assertThat(result).containsEntry("locationId", locationId.toString());
        verify(cycleCountService).startCount(eq(locationId));
    }

    @Test
    void executeReleaseWaveDelegatesToPickingWaveService() {
        UUID waveId = UUID.randomUUID();
        PickingWave wave = new PickingWave();
        wave.setId(waveId);
        when(pickingWaveService.releaseWave(waveId))
                .thenReturn(new PickingWaveService.WaveResult(wave, null, List.of()));

        Map<String, Object> result = tools.execute("releaseWave", Map.of("waveId", waveId.toString()));

        assertThat(result).containsEntry("ok", true);
        assertThat(result).containsEntry("waveId", waveId.toString());
        assertThat(result).containsEntry("taskCount", 0);
    }

    @Test
    void executeUnknownActionFailsSoftly() {
        Map<String, Object> result = tools.execute("deleteWarehouse", Map.of());
        assertThat(result).containsEntry("ok", false);
        assertThat(result).containsEntry("error", "UNKNOWN_ACTION");
    }
}

package com.invsys.support;

import com.invsys.service.CycleCountService;
import com.invsys.service.InventoryService;
import com.invsys.service.PickingWaveService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Native platform tools exposed to Spring AI ChatClient (and heuristic action proposals).
 * Proposals are confirm-gated in the UI; {@link #execute} runs after user click.
 */
@Component
public class SupportAgentTools {

    private final CycleCountService cycleCountService;
    private final PickingWaveService pickingWaveService;
    private final InventoryService inventoryService;

    public SupportAgentTools(
            CycleCountService cycleCountService,
            PickingWaveService pickingWaveService,
            InventoryService inventoryService
    ) {
        this.cycleCountService = cycleCountService;
        this.pickingWaveService = pickingWaveService;
        this.inventoryService = inventoryService;
    }

    @Tool(description = "Propose generating a cycle count for a warehouse zone/bin. "
            + "Returns a UI action_button JSON; does not mutate inventory until the user confirms.")
    public String proposeGenerateCycleCount(
            @ToolParam(description = "Zone or location barcode / path, e.g. Aisle 4 or A-01-01") String zoneId
    ) {
        return toJson(SupportActionProposal.button(
                "generateCycleCount",
                "Generate cycle count for " + zoneId,
                Map.of("zoneId", zoneId == null ? "" : zoneId.trim())));
    }

    @Tool(description = "Propose releasing a pick wave so floor pickers can claim tasks. "
            + "Returns a UI action_button JSON; does not release until the user confirms.")
    public String proposeReleaseWave(
            @ToolParam(description = "Wave UUID to release") String waveId
    ) {
        return toJson(SupportActionProposal.button(
                "releaseWave",
                "Release wave",
                Map.of("waveId", waveId == null ? "" : waveId.trim())));
    }

    /** Execute a confirmed UI action (authenticated controller entry). */
    public Map<String, Object> execute(String action, Map<String, String> params) {
        String act = action == null ? "" : action.trim();
        Map<String, String> p = params == null ? Map.of() : params;
        return switch (act) {
            case "generateCycleCount" -> {
                String zone = p.getOrDefault("zoneId", "");
                UUID locationId = inventoryService.resolveLocationId(zone);
                var detail = cycleCountService.startCount(locationId);
                yield Map.of(
                        "ok", true,
                        "action", act,
                        "cycleCountId", detail.id().toString(),
                        "locationId", locationId.toString());
            }
            case "releaseWave" -> {
                UUID waveId = UUID.fromString(p.get("waveId"));
                var result = pickingWaveService.releaseWave(waveId);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("action", act);
                out.put("waveId", result.wave().getId().toString());
                out.put("taskCount", result.tasks().size());
                yield out;
            }
            default -> Map.of("ok", false, "error", "UNKNOWN_ACTION", "action", act);
        };
    }

    private static String toJson(SupportActionProposal proposal) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(esc(proposal.type())).append("\",");
        sb.append("\"action\":\"").append(esc(proposal.action())).append("\",");
        sb.append("\"label\":\"").append(esc(proposal.label())).append("\",");
        sb.append("\"params\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : new LinkedHashMap<>(proposal.params()).entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(esc(e.getKey())).append("\":\"")
                    .append(esc(e.getValue())).append('"');
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

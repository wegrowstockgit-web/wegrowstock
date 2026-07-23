package com.invsys.modules.fulfillment.api;

import com.invsys.modules.fulfillment.domain.ClusterToteMapping;
import com.invsys.modules.fulfillment.service.ClusterPickingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfillment/cluster")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ClusterPickingController {

    private final ClusterPickingService clusterPickingService;

    public ClusterPickingController(ClusterPickingService clusterPickingService) {
        this.clusterPickingService = clusterPickingService;
    }

    @PostMapping("/{waveId}/bind")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<ClusterToteMappingResponse> bindClusterCart(@PathVariable UUID waveId,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        return clusterPickingService.bindClusterCart(waveId, parseSlots(body)).stream()
                .map(ClusterToteMappingResponse::from)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, String> parseSlots(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        Object slotsNode = body.containsKey("slots") ? body.get("slots") : body;
        if (!(slotsNode instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<Integer, String> slots = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            int slot = Integer.parseInt(String.valueOf(entry.getKey()));
            slots.put(slot, String.valueOf(entry.getValue()));
        }
        return slots;
    }

    @GetMapping("/batches/{batchId}/pick-sequence")
    public List<ClusterPickingService.ClusterPickStep> pickSequence(@PathVariable UUID batchId) {
        return clusterPickingService.getDirectedClusterPickSequence(batchId);
    }

    public record ClusterToteMappingResponse(
            UUID id,
            UUID batchId,
            int slotIndex,
            String toteBarcode,
            UUID salesOrderId
    ) {
        static ClusterToteMappingResponse from(ClusterToteMapping mapping) {
            return new ClusterToteMappingResponse(
                    mapping.getId(),
                    mapping.getBatchId(),
                    mapping.getSlotIndex(),
                    mapping.getToteBarcode(),
                    mapping.getSalesOrderId());
        }
    }
}

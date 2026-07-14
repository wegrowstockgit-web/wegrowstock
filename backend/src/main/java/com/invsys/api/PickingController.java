package com.invsys.api;

import com.invsys.domain.PickingTask;
import com.invsys.service.PickingWaveService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/picking")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class PickingController {

    private final PickingWaveService pickingWaveService;

    public PickingController(PickingWaveService pickingWaveService) {
        this.pickingWaveService = pickingWaveService;
    }

    @PostMapping("/waves/generate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public GenerateWaveResponse generateWave(@RequestBody(required = false) GenerateWaveRequest request) {
        PickingWaveService.WaveResult result = pickingWaveService.generateWave(
                request != null ? request.assignedUserId() : null,
                request != null ? request.zoneId() : null);
        return toGenerateResponse(result);
    }

    @PostMapping("/waves/{waveId}/release")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public GenerateWaveResponse releaseWave(@PathVariable UUID waveId) {
        return toGenerateResponse(pickingWaveService.releaseWave(waveId));
    }

    @GetMapping("/batches/current/tasks")
    public List<TaskResponse> currentTasks() {
        return pickingWaveService.currentBatchTasks().stream()
                .map(t -> new TaskResponse(t.getId(), t.getAllocationId(), t.getLocationPath(),
                        resolveZone(t.getLocationPath()), t.getSequenceOrder(), t.getStatus()))
                .toList();
    }

    @PostMapping("/tasks/{taskId}/pick")
    public TaskResponse pickTask(@PathVariable UUID taskId) {
        PickingTask task = pickingWaveService.markTaskPicked(taskId);
        return new TaskResponse(task.getId(), task.getAllocationId(), task.getLocationPath(),
                resolveZone(task.getLocationPath()), task.getSequenceOrder(), task.getStatus());
    }

    private GenerateWaveResponse toGenerateResponse(PickingWaveService.WaveResult result) {
        List<TaskResponse> tasks = result.tasks().stream()
                .map(t -> new TaskResponse(t.getId(), t.getAllocationId(), t.getLocationPath(),
                        resolveZone(t.getLocationPath()), t.getSequenceOrder(), t.getStatus()))
                .toList();
        UUID batchId = result.batch() != null ? result.batch().getId() : null;
        return new GenerateWaveResponse(result.wave().getId(), batchId, result.wave().getStatus(), tasks);
    }

    private static String resolveZone(String path) {
        if (path == null) {
            return "—";
        }
        String[] segments = path.split("/");
        return segments.length > 1 ? segments[1] : segments[0];
    }

    public record GenerateWaveRequest(UUID assignedUserId, UUID zoneId) {
    }

    public record GenerateWaveResponse(UUID waveId, UUID batchId, String status, List<TaskResponse> tasks) {
    }

    public record TaskResponse(UUID id, UUID allocationId, String locationPath, String zone,
                               int sequenceOrder, String status) {
    }
}

package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.Location;
import com.invsys.domain.PickingBatch;
import com.invsys.domain.PickingTask;
import com.invsys.domain.PickingWave;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.PickingBatchRepository;
import com.invsys.repository.PickingTaskRepository;
import com.invsys.repository.PickingWaveRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PickingWaveService {

    private final PickingWaveRepository waveRepository;
    private final PickingBatchRepository batchRepository;
    private final PickingTaskRepository taskRepository;
    private final AllocationRepository allocationRepository;
    private final LocationRepository locationRepository;
    private final PickingService pickingService;

    public PickingWaveService(PickingWaveRepository waveRepository,
                              PickingBatchRepository batchRepository,
                              PickingTaskRepository taskRepository,
                              AllocationRepository allocationRepository,
                              LocationRepository locationRepository,
                              PickingService pickingService) {
        this.waveRepository = waveRepository;
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
        this.allocationRepository = allocationRepository;
        this.locationRepository = locationRepository;
        this.pickingService = pickingService;
    }

    /**
     * Builds a DRAFT wave/batch so {@link PathOptimizationScheduler} can continuously
     * re-sequence pending tasks before an explicit floor release.
     */
    @Transactional
    public WaveResult generateWave(UUID assignedUserId, UUID zoneId) {
        UUID tenantId = TenantContext.requireTenantId();

        PickingWave wave = new PickingWave();
        wave.setTenantId(tenantId);
        wave.setStatus("DRAFT");
        wave = waveRepository.save(wave);

        PickingBatch batch = new PickingBatch();
        batch.setTenantId(tenantId);
        batch.setWaveId(wave.getId());
        batch.setAssignedUserId(assignedUserId);
        batch.setZoneId(zoneId);
        batch.setStatus("DRAFT");
        batch = batchRepository.save(batch);

        List<Location> locations = locationRepository.findByTenantIdOrderByPathAsc(tenantId);
        Map<UUID, String> locationPaths = locations.stream()
                .collect(Collectors.toMap(Location::getId, Location::getPath, (a, b) -> a));
        Map<UUID, Integer> sequenceIndexes = locations.stream()
                .collect(Collectors.toMap(Location::getId, Location::getSequenceIndex, (a, b) -> a, HashMap::new));

        List<Allocation> active = allocationRepository.findByTenantIdAndStatus(tenantId, "ACTIVE").stream()
                .filter(a -> a.getSalesOrderLineId() != null)
                .toList();

        List<Allocation> optimizedRoute = pickingService.optimizePickSequence(active, locationPaths, sequenceIndexes);
        pickingService.lockLevelsForRoute(tenantId, optimizedRoute);

        List<PickingTask> tasks = new ArrayList<>();
        int seq = 1;
        for (Allocation allocation : optimizedRoute) {
            PickingTask task = new PickingTask();
            task.setTenantId(tenantId);
            task.setBatchId(batch.getId());
            task.setAllocationId(allocation.getId());
            task.setLocationPath(locationPaths.getOrDefault(allocation.getLocationId(), "UNKNOWN"));
            task.setSequenceOrder(seq++);
            task.setStatus("PENDING");
            tasks.add(taskRepository.save(task));
        }

        return new WaveResult(wave, batch, tasks);
    }

    @Transactional
    public WaveResult releaseWave(UUID waveId) {
        UUID tenantId = TenantContext.requireTenantId();
        PickingWave wave = waveRepository.findById(waveId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wave not found"));
        if (!tenantId.equals(wave.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wave not found");
        }

        // Final optimization pass before floor release.
        List<PickingBatch> batches = batchRepository.findByWaveId(waveId);
        for (PickingBatch batch : batches) {
            List<PickingTask> pending = taskRepository.findByBatchIdAndStatusOrderBySequenceOrderAsc(
                    batch.getId(), "PENDING");
            if (pending.size() > 1) {
                Map<String, Integer> pathToSeq = locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                        .collect(Collectors.toMap(Location::getPath, Location::getSequenceIndex, (a, b) -> a, HashMap::new));
                List<String> paths = pending.stream().map(PickingTask::getLocationPath).toList();
                List<String> optimized = pickingService.optimizePendingPaths(paths, pathToSeq);
                Map<String, List<PickingTask>> byPath = new HashMap<>();
                for (PickingTask task : pending) {
                    byPath.computeIfAbsent(task.getLocationPath(), k -> new ArrayList<>()).add(task);
                }
                int seq = 1;
                for (String path : optimized) {
                    List<PickingTask> bucket = byPath.get(path);
                    if (bucket == null || bucket.isEmpty()) {
                        continue;
                    }
                    PickingTask task = bucket.removeFirst();
                    task.setSequenceOrder(seq++);
                    taskRepository.save(task);
                }
            }
            batch.setStatus("RELEASED");
            batchRepository.save(batch);
        }

        wave.setStatus("RELEASED");
        wave = waveRepository.save(wave);

        PickingBatch primary = batches.isEmpty() ? null : batches.getFirst();
        List<PickingTask> tasks = primary == null
                ? List.of()
                : taskRepository.findByBatchIdOrderBySequenceOrderAsc(primary.getId());
        return new WaveResult(wave, primary, tasks);
    }

    @Transactional(readOnly = true)
    public List<PickingTask> currentBatchTasks() {
        UUID tenantId = TenantContext.requireTenantId();
        return batchRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "RELEASED")
                .map(batch -> taskRepository.findByBatchIdOrderBySequenceOrderAsc(batch.getId()))
                .orElse(List.of());
    }

    @Transactional
    public PickingTask markTaskPicked(UUID taskId) {
        PickingTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("PICKED");
        return taskRepository.save(task);
    }

    public record WaveResult(PickingWave wave, PickingBatch batch, List<PickingTask> tasks) {
    }
}

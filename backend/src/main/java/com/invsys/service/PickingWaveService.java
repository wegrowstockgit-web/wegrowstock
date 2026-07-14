package com.invsys.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    @Transactional
    public WaveResult generateWave(UUID assignedUserId, UUID zoneId) {
        UUID tenantId = TenantContext.requireTenantId();

        PickingWave wave = new PickingWave();
        wave.setTenantId(tenantId);
        wave.setStatus("RELEASED");
        wave = waveRepository.save(wave);

        PickingBatch batch = new PickingBatch();
        batch.setTenantId(tenantId);
        batch.setWaveId(wave.getId());
        batch.setAssignedUserId(assignedUserId);
        batch.setZoneId(zoneId);
        batch.setStatus("RELEASED");
        batch = batchRepository.save(batch);

        Map<UUID, String> locationPaths = locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .collect(Collectors.toMap(Location::getId, Location::getPath, (a, b) -> a));

        List<Allocation> active = allocationRepository.findByTenantIdAndStatus(tenantId, "ACTIVE").stream()
                .filter(a -> a.getSalesOrderLineId() != null)
                .toList();

        List<Allocation> optimizedRoute = pickingService.optimizePickSequence(active, locationPaths);
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

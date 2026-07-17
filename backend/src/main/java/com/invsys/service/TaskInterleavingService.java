package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.CycleCount;
import com.invsys.domain.LicensePlate;
import com.invsys.domain.Location;
import com.invsys.domain.PickingBatch;
import com.invsys.domain.PickingTask;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.CycleCountRepository;
import com.invsys.repository.LicensePlateRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.PickingBatchRepository;
import com.invsys.repository.PickingTaskRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Returns the closest pending floor task (pick / putaway / count) to eliminate dead-head travel.
 */
@Service
public class TaskInterleavingService {

    private final LocationRepository locationRepository;
    private final PickingBatchRepository batchRepository;
    private final PickingTaskRepository taskRepository;
    private final AllocationRepository allocationRepository;
    private final CycleCountRepository cycleCountRepository;
    private final LicensePlateRepository licensePlateRepository;

    public TaskInterleavingService(LocationRepository locationRepository,
                                   PickingBatchRepository batchRepository,
                                   PickingTaskRepository taskRepository,
                                   AllocationRepository allocationRepository,
                                   CycleCountRepository cycleCountRepository,
                                   LicensePlateRepository licensePlateRepository) {
        this.locationRepository = locationRepository;
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
        this.allocationRepository = allocationRepository;
        this.cycleCountRepository = cycleCountRepository;
        this.licensePlateRepository = licensePlateRepository;
    }

    @Transactional(readOnly = true)
    public NextBestAction nextBestAction(UUID currentLocationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Location current = locationRepository.findById(currentLocationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Location not found"));

        Map<UUID, Location> locations = new HashMap<>();
        for (Location loc : locationRepository.findByTenantIdOrderByPathAsc(tenantId)) {
            locations.put(loc.getId(), loc);
        }

        List<Candidate> candidates = new ArrayList<>();

        // Pending picks on the active RELEASED batch
        batchRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "RELEASED")
                .ifPresent(batch -> addPickCandidates(batch, locations, candidates));

        // Open cycle counts
        for (CycleCount count : cycleCountRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "IN_PROGRESS")) {
            Location loc = locations.get(count.getLocationId());
            if (loc == null) {
                continue;
            }
            candidates.add(new Candidate(
                    "COUNT",
                    count.getId(),
                    count.getLocationId(),
                    loc.getPath(),
                    count.getNotes() != null ? count.getNotes() : "Priority cycle count",
                    null));
        }

        // OPEN LPNs away from the worker = putaway / bulk-move objectives
        for (LicensePlate lpn : licensePlateRepository.findByTenantIdAndStatus(tenantId, "OPEN")) {
            if (lpn.getLocationId() == null || lpn.getLocationId().equals(currentLocationId)) {
                continue;
            }
            Location loc = locations.get(lpn.getLocationId());
            if (loc == null) {
                continue;
            }
            candidates.add(new Candidate(
                    "PUTAWAY",
                    lpn.getId(),
                    lpn.getLocationId(),
                    loc.getPath(),
                    "Putaway / move LPN " + lpn.getLpnBarcode() + " at " + loc.getPath(),
                    null));
        }

        if (candidates.isEmpty()) {
            return new NextBestAction(null, null, null, null, null, null, "No pending floor tasks");
        }

        PathCoord origin = PathCoord.from(current.getPath(), current.getSequenceIndex());
        Candidate best = candidates.stream()
                .min(Comparator.comparingDouble(c -> {
                    Location loc = locations.get(c.locationId());
                    if (loc == null) {
                        return Double.MAX_VALUE;
                    }
                    return origin.distanceTo(PathCoord.from(loc.getPath(), loc.getSequenceIndex()));
                }))
                .orElseThrow();

        double distance = origin.distanceTo(PathCoord.from(
                locations.get(best.locationId()).getPath(),
                locations.get(best.locationId()).getSequenceIndex()));

        return new NextBestAction(
                best.taskType(),
                best.taskId(),
                best.locationId(),
                best.locationPath(),
                best.instruction(),
                best.toteIdentifier(),
                "Closest " + best.taskType().toLowerCase() + " · travel score " + Math.round(distance));
    }

    private void addPickCandidates(PickingBatch batch, Map<UUID, Location> locations, List<Candidate> candidates) {
        for (PickingTask task : taskRepository.findByBatchIdAndStatusOrderBySequenceOrderAsc(batch.getId(), "PENDING")) {
            Allocation allocation = allocationRepository.findById(task.getAllocationId()).orElse(null);
            if (allocation == null) {
                continue;
            }
            Location loc = locations.get(allocation.getLocationId());
            if (loc == null) {
                continue;
            }
            candidates.add(new Candidate(
                    "PICK",
                    task.getId(),
                    allocation.getLocationId(),
                    loc.getPath(),
                    "Pick at " + loc.getPath()
                            + (task.getToteIdentifier() != null ? " → " + task.getToteIdentifier() : ""),
                    task.getToteIdentifier()));
        }
    }

    private record Candidate(
            String taskType,
            UUID taskId,
            UUID locationId,
            String locationPath,
            String instruction,
            String toteIdentifier
    ) {
    }

    private record PathCoord(int warehouse, int zone, int aisle, int bin, int sequenceIndex) {
        static PathCoord from(String path, int sequenceIndex) {
            String[] parts = path == null ? new String[0] : path.split("/");
            return new PathCoord(hash(parts, 0), hash(parts, 1), hash(parts, 2), hash(parts, 3), sequenceIndex);
        }

        private static int hash(String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            return Math.abs(parts[index].hashCode() % 10_000);
        }

        double distanceTo(PathCoord other) {
            double seqDelta = Math.abs(sequenceIndex - other.sequenceIndex);
            return seqDelta * 50
                    + Math.abs(warehouse - other.warehouse) * 1000
                    + Math.abs(zone - other.zone) * 100
                    + Math.abs(aisle - other.aisle) * 10
                    + Math.abs(bin - other.bin);
        }
    }

    public record NextBestAction(
            String taskType,
            UUID taskId,
            UUID locationId,
            String locationPath,
            String instruction,
            String toteIdentifier,
            String summary
    ) {
    }
}

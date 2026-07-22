package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.PickingBatch;
import com.invsys.domain.PickingTask;
import com.invsys.modules.fulfillment.domain.PickingWave;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.PickingBatchRepository;
import com.invsys.repository.PickingTaskRepository;
import com.invsys.modules.fulfillment.repository.PickingWaveRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.invsys.modules.fulfillment.service.AllocationService;
import com.invsys.modules.fulfillment.service.PickingService;

@Service
public class PickingWaveService {

    private final PickingWaveRepository waveRepository;
    private final PickingBatchRepository batchRepository;
    private final PickingTaskRepository taskRepository;
    private final AllocationRepository allocationRepository;
    private final LocationRepository locationRepository;
    private final PickingService pickingService;
    private final CrossDockService crossDockService;
    private final AllocationService allocationService;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final DSLContext dsl;

    public PickingWaveService(PickingWaveRepository waveRepository,
                              PickingBatchRepository batchRepository,
                              PickingTaskRepository taskRepository,
                              AllocationRepository allocationRepository,
                              LocationRepository locationRepository,
                              PickingService pickingService,
                              CrossDockService crossDockService,
                              AllocationService allocationService,
                              SalesOrderLineRepository salesOrderLineRepository,
                              DSLContext dsl) {
        this.waveRepository = waveRepository;
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
        this.allocationRepository = allocationRepository;
        this.locationRepository = locationRepository;
        this.pickingService = pickingService;
        this.crossDockService = crossDockService;
        this.allocationService = allocationService;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.dsl = dsl;
    }

    /**
     * Cross-dock checker: inbound open PO lines vs unfulfilled sales backorders.
     */
    @Transactional(readOnly = true)
    public List<CrossDockService.CrossDockSuggestion> crossDockSuggestions() {
        return crossDockService.suggestions();
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
        assignToteIdentifiers(tasks);

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

    /**
     * Path-optimized pick list for a wave: allocations sorted by {@code locations.path ASC}
     * so operators walk a deterministic physical loop.
     */
    @Transactional(readOnly = true)
    public List<WavePick> listPicksByPath(UUID waveId) {
        UUID tenantId = TenantContext.requireTenantId();
        PickingWave wave = waveRepository.findById(waveId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wave not found"));
        if (!tenantId.equals(wave.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wave not found");
        }

        Result<Record> rows = dsl.fetch("""
                SELECT pt.id AS task_id,
                       pt.allocation_id AS allocation_id,
                       a.variant_id AS variant_id,
                       a.location_id AS location_id,
                       a.quantity AS quantity,
                       l.path AS location_path,
                       pt.sequence_order AS sequence_order,
                       pt.status AS status
                FROM picking_tasks pt
                JOIN picking_batches pb ON pb.id = pt.batch_id AND pb.tenant_id = pt.tenant_id
                JOIN allocations a ON a.id = pt.allocation_id AND a.tenant_id = pt.tenant_id
                JOIN locations l ON l.id = a.location_id AND l.tenant_id = pt.tenant_id
                WHERE pt.tenant_id = ?
                  AND pb.wave_id = ?
                ORDER BY l.path ASC, pt.sequence_order ASC
                """, tenantId, waveId);

        List<WavePick> picks = new ArrayList<>(rows.size());
        for (Record row : rows) {
            picks.add(new WavePick(
                    row.get("task_id", UUID.class),
                    row.get("allocation_id", UUID.class),
                    row.get("variant_id", UUID.class),
                    row.get("location_id", UUID.class),
                    row.get("quantity", java.math.BigDecimal.class),
                    row.get("location_path", String.class),
                    row.get("sequence_order", Integer.class),
                    row.get("status", String.class)));
        }
        return picks;
    }

    @Transactional
    public PickingTask markTaskPicked(UUID taskId) {
        PickingTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("PICKED");
        task = taskRepository.save(task);

        PickingBatch batch = batchRepository.findById(task.getBatchId()).orElse(null);
        if (batch != null) {
            List<PickingTask> tasks = taskRepository.findByBatchIdOrderBySequenceOrderAsc(batch.getId());
            boolean allDone = tasks.stream()
                    .allMatch(t -> "PICKED".equals(t.getStatus()) || "SKIPPED".equals(t.getStatus()));
            if (allDone) {
                batch.setStatus("COMPLETED");
                batch.setCompletedAt(Instant.now());
                batchRepository.save(batch);
                List<PickingBatch> waveBatches = batchRepository.findByWaveId(batch.getWaveId());
                boolean waveDone = waveBatches.stream()
                        .allMatch(b -> "COMPLETED".equals(b.getStatus()) || "DRAFT".equals(b.getStatus()));
                if (waveDone) {
                    waveRepository.findById(batch.getWaveId()).ifPresent(wave -> {
                        wave.setStatus("COMPLETED");
                        waveRepository.save(wave);
                    });
                }
            }
        }
        return task;
    }

    /**
     * Algorithmic pick-path optimization: aggregate open unfulfilled sales-order lines
     * (optionally filtered), resolve leaf BIN coordinates, group variants, and sequence
     * by location path (Warehouse → Zone → Aisle → Bin) to prevent backtracking.
     */
    @Transactional
    public OptimizeResult optimizeWave(List<UUID> salesOrderIds) {
        UUID tenantId = TenantContext.requireTenantId();

        List<Allocation> active = allocationRepository.findByTenantIdAndStatus(tenantId, "ACTIVE").stream()
                .filter(a -> a.getSalesOrderLineId() != null)
                .toList();

        if (salesOrderIds != null && !salesOrderIds.isEmpty()) {
            UUID[] orderIds = salesOrderIds.toArray(UUID[]::new);
            Result<Record> lineRows = dsl.fetch("""
                    SELECT sol.id AS line_id
                    FROM sales_order_lines sol
                    WHERE sol.tenant_id = ?
                      AND sol.sales_order_id = ANY (?::uuid[])
                    """, tenantId, (Object) orderIds);
            var allowedLines = lineRows.stream()
                    .map(r -> r.get("line_id", UUID.class))
                    .collect(Collectors.toSet());
            active = active.stream()
                    .filter(a -> allowedLines.contains(a.getSalesOrderLineId()))
                    .toList();
        }

        // Prefer BIN leaf coordinates when available.
        List<Location> locations = locationRepository.findByTenantIdOrderByPathAsc(tenantId);
        Map<UUID, String> locationPaths = locations.stream()
                .collect(Collectors.toMap(Location::getId, Location::getPath, (a, b) -> a));
        Map<UUID, Integer> sequenceIndexes = locations.stream()
                .collect(Collectors.toMap(Location::getId, Location::getSequenceIndex, (a, b) -> a, HashMap::new));

        // Group same-variant picks together before path sequencing.
        Map<UUID, List<Allocation>> byVariant = active.stream()
                .collect(Collectors.groupingBy(Allocation::getVariantId, LinkedHashMap::new, Collectors.toList()));
        List<Allocation> grouped = new ArrayList<>();
        byVariant.values().forEach(grouped::addAll);

        // A* nearest-neighbor route — do not re-sort by path (would discard the spatial optimum).
        List<Allocation> optimizedRoute = pickingService.optimizePickSequence(
                grouped, locationPaths, sequenceIndexes);

        PickingWave wave = new PickingWave();
        wave.setTenantId(tenantId);
        wave.setStatus("DRAFT");
        wave = waveRepository.save(wave);

        PickingBatch batch = new PickingBatch();
        batch.setTenantId(tenantId);
        batch.setWaveId(wave.getId());
        batch.setStatus("DRAFT");
        batch = batchRepository.save(batch);

        pickingService.lockLevelsForRoute(tenantId, optimizedRoute);

        List<OptimizedPickLine> manifest = new ArrayList<>();
        List<PickingTask> tasks = new ArrayList<>();
        int seq = 1;
        for (Allocation allocation : optimizedRoute) {
            String path = locationPaths.getOrDefault(allocation.getLocationId(), "UNKNOWN");
            PickingTask task = new PickingTask();
            task.setTenantId(tenantId);
            task.setBatchId(batch.getId());
            task.setAllocationId(allocation.getId());
            task.setLocationPath(path);
            task.setSequenceOrder(seq);
            task.setStatus("PENDING");
            task = taskRepository.save(task);
            tasks.add(task);
            manifest.add(new OptimizedPickLine(
                    seq,
                    task.getId(),
                    allocation.getId(),
                    allocation.getVariantId(),
                    allocation.getLocationId(),
                    allocation.getQuantity(),
                    path,
                    pathSegments(path)));
            seq++;
        }
        assignToteIdentifiers(tasks);

        return new OptimizeResult(wave.getId(), batch.getId(), wave.getStatus(), manifest, tasks);
    }

    /**
     * MIB tote routing: one tote letter per distinct sales order in the wave.
     */
    private void assignToteIdentifiers(List<PickingTask> tasks) {
        Map<UUID, String> soToTote = new LinkedHashMap<>();
        for (PickingTask task : tasks) {
            Allocation allocation = allocationRepository.findById(task.getAllocationId()).orElse(null);
            UUID salesOrderId = null;
            if (allocation != null && allocation.getSalesOrderLineId() != null) {
                salesOrderId = salesOrderLineRepository.findById(allocation.getSalesOrderLineId())
                        .map(SalesOrderLine::getSalesOrderId)
                        .orElse(null);
            }
            String tote;
            if (salesOrderId == null) {
                tote = "Tote ?";
            } else {
                tote = soToTote.computeIfAbsent(salesOrderId,
                        id -> "Tote " + (char) ('A' + Math.min(soToTote.size(), 25)));
            }
            task.setToteIdentifier(tote);
            taskRepository.save(task);
        }
    }

    private static List<String> pathSegments(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return List.of(path.split("/"));
    }

    /**
     * Pre-emptive device lock: assign all ACTIVE allocations in the wave to the current picker.
     */
    @Transactional
    public ClaimResult claimWave(UUID waveId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        PickingWave wave = waveRepository.findById(waveId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wave not found"));
        if (!tenantId.equals(wave.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wave not found");
        }

        Instant claimedAt = Instant.now();
        List<UUID> allocationIds = new ArrayList<>();
        List<PickingBatch> batches = batchRepository.findByWaveId(waveId);
        for (PickingBatch batch : batches) {
            batch.setAssignedUserId(userId);
            if (batch.getClaimedAt() == null) {
                batch.setClaimedAt(claimedAt);
            }
            batchRepository.save(batch);
            for (PickingTask task : taskRepository.findByBatchIdOrderBySequenceOrderAsc(batch.getId())) {
                if (task.getAllocationId() != null) {
                    allocationIds.add(task.getAllocationId());
                }
            }
        }
        int claimed = allocationService.claimAllocations(allocationIds, userId);
        return new ClaimResult(wave.getId(), userId, claimed);
    }

    public record WaveResult(PickingWave wave, PickingBatch batch, List<PickingTask> tasks) {
    }

    public record ClaimResult(UUID waveId, UUID assignedToUserId, int allocationsClaimed) {
    }

    public record WavePick(
            UUID taskId,
            UUID allocationId,
            UUID variantId,
            UUID locationId,
            java.math.BigDecimal quantity,
            String locationPath,
            int sequenceOrder,
            String status
    ) {
    }

    public record OptimizeResult(
            UUID waveId,
            UUID batchId,
            String status,
            List<OptimizedPickLine> manifest,
            List<PickingTask> tasks
    ) {
    }

    public record OptimizedPickLine(
            int sequenceOrder,
            UUID taskId,
            UUID allocationId,
            UUID variantId,
            UUID locationId,
            java.math.BigDecimal quantity,
            String locationPath,
            List<String> pathSegments
    ) {
    }
}

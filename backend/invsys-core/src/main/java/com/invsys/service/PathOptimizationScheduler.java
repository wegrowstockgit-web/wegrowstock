package com.invsys.service;

import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.PickingBatch;
import com.invsys.domain.PickingTask;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.PickingBatchRepository;
import com.invsys.repository.PickingTaskRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import com.invsys.modules.fulfillment.service.PickingService;

/**
 * Continuously re-evaluates DRAFT picking batches and re-sequences pending tasks
 * to minimize travel latency before waves are released to the floor.
 */
@Service
public class PathOptimizationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PathOptimizationScheduler.class);

    private final TenantRepository tenantRepository;
    private final PickingBatchRepository batchRepository;
    private final PickingTaskRepository taskRepository;
    private final LocationRepository locationRepository;
    private final PickingService pickingService;
    private final TransactionTemplate transactionTemplate;
    private final Executor virtualThreadExecutor;

    public PathOptimizationScheduler(TenantRepository tenantRepository,
                                     PickingBatchRepository batchRepository,
                                     PickingTaskRepository taskRepository,
                                     LocationRepository locationRepository,
                                     PickingService pickingService,
                                     TransactionTemplate transactionTemplate,
                                     @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.tenantRepository = tenantRepository;
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
        this.locationRepository = locationRepository;
        this.pickingService = pickingService;
        this.transactionTemplate = transactionTemplate;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Scheduled(fixedDelayString = "${invsys.picking.path-optimize-interval-ms:15000}")
    public void optimizePendingPaths() {
        List<UUID> tenantIds = tenantRepository.findAll().stream().map(t -> t.getId()).toList();
        for (UUID tenantId : tenantIds) {
            virtualThreadExecutor.execute(() -> optimizeTenant(tenantId));
        }
    }

    void optimizeTenant(UUID tenantId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                TenantContext.setTenantId(tenantId);
                try {
                    List<PickingBatch> drafts = batchRepository.findByTenantIdAndStatus(tenantId, "DRAFT");
                    for (PickingBatch batch : drafts) {
                        resequenceBatch(tenantId, batch);
                    }
                } finally {
                    TenantContext.clear();
                }
            });
        } catch (Exception e) {
            log.warn("Path optimization failed for tenant={}", tenantId, e);
            TenantContext.clear();
        }
    }

    private void resequenceBatch(UUID tenantId, PickingBatch batch) {
        List<PickingTask> pending = taskRepository.findByBatchIdAndStatusOrderBySequenceOrderAsc(
                batch.getId(), "PENDING");
        if (pending.size() <= 1) {
            return;
        }

        Map<String, Integer> pathToSeq = locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .collect(Collectors.toMap(Location::getPath, Location::getSequenceIndex, (a, b) -> a, HashMap::new));

        List<String> originalPaths = pending.stream().map(PickingTask::getLocationPath).toList();
        List<String> optimizedPaths = pickingService.optimizePendingPaths(originalPaths, pathToSeq);

        // Map optimized path order back onto tasks (stable for duplicate paths).
        Map<String, List<PickingTask>> byPath = new HashMap<>();
        for (PickingTask task : pending) {
            byPath.computeIfAbsent(task.getLocationPath(), k -> new ArrayList<>()).add(task);
        }

        int seq = 1;
        boolean changed = false;
        for (String path : optimizedPaths) {
            List<PickingTask> bucket = byPath.get(path);
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            PickingTask task = bucket.removeFirst();
            if (task.getSequenceOrder() != seq) {
                task.setSequenceOrder(seq);
                taskRepository.save(task);
                changed = true;
            }
            seq++;
        }

        if (changed) {
            log.debug("Re-sequenced {} pending tasks for batch={}", pending.size(), batch.getId());
        }
    }
}

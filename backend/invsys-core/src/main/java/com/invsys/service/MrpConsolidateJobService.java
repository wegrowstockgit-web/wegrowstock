package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class MrpConsolidateJobService {

    private final MrpCalculationEngine mrpCalculationEngine;
    private final Executor virtualThreadExecutor;
    private final ConcurrentHashMap<UUID, Job> jobs = new ConcurrentHashMap<>();

    public MrpConsolidateJobService(MrpCalculationEngine mrpCalculationEngine,
                                    @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.mrpCalculationEngine = mrpCalculationEngine;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    public Job enqueue(MrpCalculationEngine.MrpCalculateCommand command) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId().orElse(null);
        UUID warehouseId = TenantContext.getWarehouseId().orElse(null);
        UUID jobId = UUID.randomUUID();
        Job queued = new Job(jobId, "QUEUED", null, null);
        jobs.put(jobId, queued);
        virtualThreadExecutor.execute(() -> {
            TenantContext.setTenantId(tenantId);
            if (userId != null) {
                TenantContext.setUserId(userId);
            }
            if (warehouseId != null) {
                TenantContext.setWarehouseId(warehouseId);
            }
            try {
                MrpCalculationEngine.MrpRunResult result = mrpCalculationEngine.calculateAndCreateDraftPos(command);
                jobs.put(jobId, new Job(jobId, "COMPLETED", result, null));
            } catch (Exception ex) {
                String message = ex.getMessage() != null ? ex.getMessage() : "MRP consolidate failed";
                jobs.put(jobId, new Job(jobId, "FAILED", null, message));
            } finally {
                TenantContext.clear();
            }
        });
        return queued;
    }

    public Job requireJob(UUID jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "MRP consolidate job not found");
        }
        return job;
    }

    public record Job(
            UUID jobId,
            String status,
            MrpCalculationEngine.MrpRunResult result,
            String error
    ) {
        public Job {
            status = status == null ? "QUEUED" : status;
        }
    }
}

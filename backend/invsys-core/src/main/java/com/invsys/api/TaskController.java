package com.invsys.api;

import com.invsys.service.TaskOrchestratorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.Tenant;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskOrchestratorService taskOrchestratorService;

    public TaskController(TaskOrchestratorService taskOrchestratorService) {
        this.taskOrchestratorService = taskOrchestratorService;
    }

    /**
     * Closest interleaved floor task for the authenticated tenant (picks, putaway,
     * counts, predictive replenishment). Tenant isolation via {@code TenantContext}.
     */
    @GetMapping("/next-best-action")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public TaskOrchestratorService.NextBestAction nextBestAction(
            @RequestParam UUID currentLocationId) {
        return taskOrchestratorService.nextBestAction(currentLocationId);
    }
}

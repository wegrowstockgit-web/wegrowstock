package com.invsys.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Compatibility façade — floor interleaving is owned by {@link TaskOrchestratorService}.
 */
@Service
public class TaskInterleavingService {

    private final TaskOrchestratorService orchestrator;

    public TaskInterleavingService(TaskOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Transactional(readOnly = true)
    public TaskOrchestratorService.NextBestAction nextBestAction(UUID currentLocationId) {
        return orchestrator.nextBestAction(currentLocationId);
    }
}

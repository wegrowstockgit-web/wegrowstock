package com.invsys.api;

import com.invsys.service.TaskInterleavingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskInterleavingService taskInterleavingService;

    public TaskController(TaskInterleavingService taskInterleavingService) {
        this.taskInterleavingService = taskInterleavingService;
    }

    @GetMapping("/next-best-action")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public TaskInterleavingService.NextBestAction nextBestAction(
            @RequestParam UUID currentLocationId) {
        return taskInterleavingService.nextBestAction(currentLocationId);
    }
}

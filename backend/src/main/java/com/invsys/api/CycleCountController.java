package com.invsys.api;

import com.invsys.service.CycleCountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cycle-counts")
public class CycleCountController {

    private final CycleCountService cycleCountService;

    public CycleCountController(CycleCountService cycleCountService) {
        this.cycleCountService = cycleCountService;
    }

    @GetMapping("/priority-audits")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public List<CycleCountService.PriorityAudit> priorityAudits() {
        return cycleCountService.priorityAudits();
    }
}

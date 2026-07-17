package com.invsys.api;

import com.invsys.service.CycleCountService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public CycleCountService.BlindCountSettings settings() {
        return cycleCountService.blindCountSettings();
    }

    @GetMapping("/pending-variances")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<CycleCountService.PendingVariance> pendingVariances() {
        return cycleCountService.pendingVariances();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public CycleCountService.CycleCountDetail start(@RequestBody StartCountRequest body) {
        return cycleCountService.startCount(body.locationId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public CycleCountService.CycleCountDetail get(@PathVariable UUID id) {
        return cycleCountService.getCount(id);
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public CycleCountService.CycleCountDetail open(@PathVariable UUID id) {
        return cycleCountService.openCount(id);
    }

    @PostMapping("/{id}/lines/{lineId}/submit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public CycleCountService.CycleCountLineView submit(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @RequestBody SubmitCountRequest body) {
        return cycleCountService.submitCountedQty(id, lineId, body.countedQty());
    }

    @PostMapping("/lines/{lineId}/approve-adjustment")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public CycleCountService.CycleCountLineView approveAdjustment(@PathVariable UUID lineId) {
        return cycleCountService.approveLedgerAdjustment(lineId);
    }

    @PostMapping("/lines/{lineId}/request-recount")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public CycleCountService.CycleCountLineView requestRecount(@PathVariable UUID lineId) {
        return cycleCountService.requestRecount(lineId);
    }

    public record StartCountRequest(@NotNull UUID locationId) {
    }

    public record SubmitCountRequest(
            @NotNull
            @DecimalMin(value = "0", inclusive = true)
            BigDecimal countedQty
    ) {
    }
}

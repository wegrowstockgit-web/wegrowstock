package com.invsys.api;

import com.invsys.api.dto.OfflineSyncConflictView;
import com.invsys.service.OfflineSyncConflictService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offline-sync-conflicts")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class OfflineSyncConflictController {

    private final OfflineSyncConflictService conflictService;

    public OfflineSyncConflictController(OfflineSyncConflictService conflictService) {
        this.conflictService = conflictService;
    }

    @GetMapping
    public List<OfflineSyncConflictView> list(@RequestParam(required = false) String status) {
        return conflictService.listViews(status);
    }

    @PostMapping("/{id}/dismiss")
    public OfflineSyncConflictView dismiss(@PathVariable UUID id) {
        return conflictService.dismiss(id);
    }

    @PostMapping("/{id}/retry")
    public OfflineSyncConflictView forceRetry(@PathVariable UUID id) {
        return conflictService.forceRetry(id);
    }

    @PostMapping("/{id}/resolved")
    public OfflineSyncConflictView markResolved(@PathVariable UUID id) {
        return conflictService.markResolved(id);
    }

    /**
     * Approve & re-process: apply manager corrections, stamp ledger as manager with
     * {@code OFFLINE_CONFLICT_OVERRIDE}, mark {@code RESOLVED_AND_REPLAYED}.
     */
    @PostMapping("/{id}/resolve")
    public OfflineSyncConflictView resolve(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveConflictRequest body
    ) {
        Map<String, Object> corrections = body != null && body.corrections() != null
                ? body.corrections()
                : Map.of();
        return conflictService.resolveConflict(id, corrections);
    }

    public record ResolveConflictRequest(Map<String, Object> corrections) {
    }
}

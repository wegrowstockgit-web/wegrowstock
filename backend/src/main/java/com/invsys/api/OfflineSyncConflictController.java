package com.invsys.api;

import com.invsys.domain.OfflineSyncConflict;
import com.invsys.service.OfflineSyncConflictService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    public List<OfflineSyncConflict> list(@RequestParam(required = false) String status) {
        return conflictService.list(status);
    }

    @PostMapping("/{id}/dismiss")
    public OfflineSyncConflict dismiss(@PathVariable UUID id) {
        return conflictService.dismiss(id);
    }

    @PostMapping("/{id}/retry")
    public OfflineSyncConflict forceRetry(@PathVariable UUID id) {
        return conflictService.forceRetry(id);
    }

    @PostMapping("/{id}/resolved")
    public OfflineSyncConflict markResolved(@PathVariable UUID id) {
        return conflictService.markResolved(id);
    }
}

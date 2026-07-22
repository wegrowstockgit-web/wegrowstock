package com.invsys.api;

import com.invsys.service.AuditQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public List<AuditEntryResponse> forEntity(@PathVariable String entityType,
                                              @PathVariable UUID entityId,
                                              @RequestParam(defaultValue = "50") int limit) {
        return auditQueryService.forEntity(entityType, entityId, limit).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/tenant")
    public TenantAuditPage tenant(@RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "50") int limit,
                                  @RequestParam(required = false) String entityType,
                                  @RequestParam(required = false) String action) {
        AuditQueryService.PageResult page = auditQueryService.forTenant(cursor, limit, entityType, action);
        return new TenantAuditPage(
                page.items().stream().map(this::toResponse).toList(),
                page.nextCursor());
    }

    private AuditEntryResponse toResponse(AuditQueryService.EnrichedAudit row) {
        return new AuditEntryResponse(
                row.id(),
                row.actorUserId(),
                row.actorEmail(),
                row.actorDisplayName(),
                row.action(),
                row.entityType(),
                row.entityId(),
                row.diff(),
                row.createdAt());
    }

    public record AuditEntryResponse(
            UUID id,
            UUID actorUserId,
            String actorEmail,
            String actorDisplayName,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> diff,
            Instant createdAt
    ) {
    }

    public record TenantAuditPage(List<AuditEntryResponse> items, String nextCursor) {
    }
}

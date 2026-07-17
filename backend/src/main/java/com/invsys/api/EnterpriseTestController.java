package com.invsys.api;

import com.invsys.service.StorageAccrualWorker;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Profile-gated hooks for enterprise E2E journeys (accrual trigger + LMS clock shift).
 * Never active under {@code prod}.
 */
/** Dev/CI-only hooks (accrual run, labor clock). Never loaded under {@code prod}. */
@RestController
@RequestMapping("/api/v1/admin/test")
@Profile({"dev", "test", "docker", "default"})
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class EnterpriseTestController {

    private final StorageAccrualWorker storageAccrualWorker;
    private final DSLContext dsl;

    public EnterpriseTestController(StorageAccrualWorker storageAccrualWorker, DSLContext dsl) {
        this.storageAccrualWorker = storageAccrualWorker;
        this.dsl = dsl;
    }

    @PostMapping("/accruals/run")
    public Map<String, Object> runAccruals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate accrualDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        int created = storageAccrualWorker.accrueForTenant(tenantId, accrualDate);
        return Map.of(
                "tenantId", tenantId,
                "accrualDate", accrualDate.toString(),
                "created", created);
    }

    /**
     * Fast-forward LMS timestamps: claimed wave starts {@code shiftHours} ago and ends now,
     * with picked tasks / ledger rows spread across that window for the operator.
     */
    @PostMapping("/labor/backdate-shift")
    @Transactional
    public Map<String, Object> backdateLaborShift(@RequestBody BackdateShiftRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = request.userId();
        double hours = request.shiftHours() != null && request.shiftHours() > 0
                ? request.shiftHours()
                : 1.0;
        Instant end = Instant.now();
        Instant start = end.minus((long) (hours * 3600), ChronoUnit.SECONDS);
        OffsetDateTime endOdt = end.atOffset(ZoneOffset.UTC);
        OffsetDateTime startOdt = start.atOffset(ZoneOffset.UTC);
        OffsetDateTime windowStart = end.minus(12, ChronoUnit.HOURS).atOffset(ZoneOffset.UTC);

        int batches = dsl.execute("""
                UPDATE picking_batches
                SET claimed_at = ?::timestamptz,
                    completed_at = COALESCE(completed_at, ?::timestamptz),
                    updated_at = ?::timestamptz
                WHERE tenant_id = ?
                  AND assigned_user_id = ?
                  AND claimed_at IS NOT NULL
                  AND claimed_at >= ?::timestamptz
                """, startOdt, endOdt, endOdt, tenantId, userId, windowStart);

        // Stamp all recent picks to shift end, then pin first for Strict Shift PPH.
        // inventory_ledger is append-only — never UPDATE it.
        int tasks = dsl.execute("""
                UPDATE picking_tasks pt
                SET updated_at = ?::timestamptz
                FROM picking_batches pb
                WHERE pt.batch_id = pb.id
                  AND pt.tenant_id = ?
                  AND pb.tenant_id = ?
                  AND pb.assigned_user_id = ?
                  AND pt.status = 'PICKED'
                  AND pb.claimed_at IS NOT NULL
                  AND pb.claimed_at >= ?::timestamptz
                """, endOdt, tenantId, tenantId, userId, windowStart);

        dsl.execute("""
                UPDATE picking_tasks
                SET updated_at = ?::timestamptz
                WHERE id = (
                    SELECT pt2.id FROM picking_tasks pt2
                    JOIN picking_batches pb2 ON pb2.id = pt2.batch_id
                    WHERE pt2.tenant_id = ?
                      AND pb2.assigned_user_id = ?
                      AND pt2.status = 'PICKED'
                      AND pb2.claimed_at IS NOT NULL
                      AND pb2.claimed_at >= ?::timestamptz
                    ORDER BY pt2.id ASC
                    LIMIT 1
                )
                """, startOdt, tenantId, userId, windowStart);

        return Map.of(
                "userId", userId,
                "shiftStart", start.toString(),
                "shiftEnd", end.toString(),
                "batchesUpdated", batches,
                "tasksUpdated", tasks);
    }

    public record BackdateShiftRequest(UUID userId, Double shiftHours) {
    }
}

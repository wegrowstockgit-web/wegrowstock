package com.invsys.service;

import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Floor Labor Velocity (LMS): Strict Shift PPH, Active Wave PPH, and Utilization %.
 */
@Service
public class LaborAnalyticsService {

    private static final BigDecimal MIN_HOURS = new BigDecimal("0.0167"); // ~1 minute floor

    private final DSLContext dsl;

    public LaborAnalyticsService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public List<OperatorVelocity> calculateOperatorVelocity(UUID tenantId, Instant start, Instant end) {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenantId();
        }
        Instant from = start != null ? start : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant to = end != null ? end : Instant.now();
        if (!to.isAfter(from)) {
            to = from.plus(1, ChronoUnit.DAYS);
        }
        // jOOQ plain SQL binds Instant as varchar unless cast — use OffsetDateTime
        OffsetDateTime fromOdt = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toOdt = to.atOffset(ZoneOffset.UTC);

        Map<UUID, MutableStats> byUser = new LinkedHashMap<>();

        // Ledger SHIP / RECEIVE / PICK (created_by)
        Result<Record> ledgerRows = dsl.fetch("""
                SELECT il.created_by AS user_id,
                       il.movement_type AS movement_type,
                       COUNT(*)::bigint AS event_count,
                       COALESCE(SUM(ABS(il.quantity_delta)), 0) AS unit_qty,
                       MIN(il.created_at) AS first_at,
                       MAX(il.created_at) AS last_at
                FROM inventory_ledger il
                WHERE il.tenant_id = ?
                  AND il.created_by IS NOT NULL
                  AND il.movement_type IN ('SHIP', 'RECEIVE', 'PICK')
                  AND il.created_at >= ?::timestamptz AND il.created_at < ?::timestamptz
                GROUP BY il.created_by, il.movement_type
                """, tenantId, fromOdt, toOdt);
        for (Record row : ledgerRows) {
            UUID userId = row.get("user_id", UUID.class);
            MutableStats stats = byUser.computeIfAbsent(userId, id -> new MutableStats());
            String type = row.get("movement_type", String.class);
            long events = row.get("event_count", Long.class);
            BigDecimal units = row.get("unit_qty", BigDecimal.class);
            if ("PICK".equals(type) || "SHIP".equals(type)) {
                // Floor picks: prefer discrete events; SHIP counts as pick velocity proxy
                stats.totalPicks += events;
                stats.pickUnits = stats.pickUnits.add(units != null ? units : BigDecimal.ZERO);
            }
            if ("RECEIVE".equals(type)) {
                stats.totalReceives += events;
            }
            Instant first = toInstant(row.get("first_at"));
            Instant last = toInstant(row.get("last_at"));
            stats.expandShift(first, last);
        }

        // Wave picks from picking_tasks (PICKED) attributed via batch assignee
        Result<Record> pickRows = dsl.fetch("""
                SELECT pb.assigned_user_id AS user_id,
                       COUNT(pt.id)::bigint AS pick_count,
                       MIN(pt.updated_at) AS first_at,
                       MAX(pt.updated_at) AS last_at
                FROM picking_tasks pt
                JOIN picking_batches pb ON pb.id = pt.batch_id AND pb.tenant_id = pt.tenant_id
                WHERE pt.tenant_id = ?
                  AND pt.status = 'PICKED'
                  AND pb.assigned_user_id IS NOT NULL
                  AND pt.updated_at >= ?::timestamptz AND pt.updated_at < ?::timestamptz
                GROUP BY pb.assigned_user_id
                """, tenantId, fromOdt, toOdt);
        for (Record row : pickRows) {
            UUID userId = row.get("user_id", UUID.class);
            MutableStats stats = byUser.computeIfAbsent(userId, id -> new MutableStats());
            long pickCount = row.get("pick_count", Long.class);
            // Prefer task picks over SHIP-as-proxy when task data exists
            if (pickCount > 0) {
                stats.waveTaskPicks += pickCount;
            }
            stats.expandShift(toInstant(row.get("first_at")), toInstant(row.get("last_at")));
        }

        // Active wave durations (claimed → completed) overlapping the window
        Result<Record> waveRows = dsl.fetch("""
                SELECT pb.assigned_user_id AS user_id,
                       pb.claimed_at AS claimed_at,
                       COALESCE(pb.completed_at, pb.updated_at, ?::timestamptz) AS ended_at
                FROM picking_batches pb
                WHERE pb.tenant_id = ?
                  AND pb.assigned_user_id IS NOT NULL
                  AND pb.claimed_at IS NOT NULL
                  AND pb.claimed_at < ?::timestamptz
                  AND COALESCE(pb.completed_at, pb.updated_at, ?::timestamptz) > ?::timestamptz
                """, toOdt, tenantId, toOdt, toOdt, fromOdt);
        for (Record row : waveRows) {
            UUID userId = row.get("user_id", UUID.class);
            MutableStats stats = byUser.computeIfAbsent(userId, id -> new MutableStats());
            Instant claimed = toInstant(row.get("claimed_at"));
            Instant ended = toInstant(row.get("ended_at"));
            if (claimed == null || ended == null || !ended.isAfter(claimed)) {
                continue;
            }
            Instant clipStart = claimed.isBefore(from) ? from : claimed;
            Instant clipEnd = ended.isAfter(to) ? to : ended;
            if (clipEnd.isAfter(clipStart)) {
                stats.activeWaveSeconds += Duration.between(clipStart, clipEnd).getSeconds();
            }
        }

        // Audit log floor actions (expand shift window when present)
        Result<Record> auditRows = dsl.fetch("""
                SELECT al.actor_user_id AS user_id,
                       MIN(al.created_at) AS first_at,
                       MAX(al.created_at) AS last_at
                FROM audit_log al
                WHERE al.tenant_id = ?
                  AND al.actor_user_id IS NOT NULL
                  AND al.created_at >= ?::timestamptz AND al.created_at < ?::timestamptz
                  AND (
                      al.action ILIKE '%PICK%'
                      OR al.action ILIKE '%SHIP%'
                      OR al.action ILIKE '%RECEIVE%'
                      OR al.entity_type IN ('PICKING_TASK', 'PICKING_BATCH', 'PICKING_WAVE', 'INVENTORY_LEDGER')
                  )
                GROUP BY al.actor_user_id
                """, tenantId, fromOdt, toOdt);
        for (Record row : auditRows) {
            UUID userId = row.get("user_id", UUID.class);
            MutableStats stats = byUser.computeIfAbsent(userId, id -> new MutableStats());
            stats.expandShift(toInstant(row.get("first_at")), toInstant(row.get("last_at")));
        }

        Map<UUID, String> names = loadDisplayNames(tenantId, byUser.keySet());
        Map<UUID, List<HourlyPoint>> hourly = loadHourlyPicks(tenantId, fromOdt, toOdt, byUser.keySet());

        List<OperatorVelocity> result = new ArrayList<>();
        for (Map.Entry<UUID, MutableStats> entry : byUser.entrySet()) {
            UUID userId = entry.getKey();
            MutableStats s = entry.getValue();
            long picks = s.waveTaskPicks > 0 ? s.waveTaskPicks : s.totalPicks;
            if (picks <= 0 && s.totalReceives <= 0) {
                continue;
            }

            double shiftHours = s.shiftSeconds() / 3600.0;
            double activeHours = s.activeWaveSeconds / 3600.0;
            if (shiftHours < MIN_HOURS.doubleValue() && s.firstAt != null && s.lastAt != null) {
                shiftHours = MIN_HOURS.doubleValue();
            }

            BigDecimal shiftPph = divide(picks, shiftHours);
            BigDecimal activePph = activeHours > 0
                    ? divide(picks, activeHours)
                    : BigDecimal.ZERO;
            BigDecimal utilization = shiftHours > 0
                    ? BigDecimal.valueOf(activeHours / shiftHours * 100.0).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            if (utilization.compareTo(new BigDecimal("100")) > 0) {
                utilization = new BigDecimal("100.0");
            }

            result.add(new OperatorVelocity(
                    userId,
                    names.getOrDefault(userId, "Operator"),
                    picks,
                    s.totalReceives,
                    activePph,
                    shiftPph,
                    utilization,
                    roundHours(activeHours),
                    roundHours(shiftHours),
                    hourly.getOrDefault(userId, List.of())));
        }

        result.sort(Comparator
                .comparing(OperatorVelocity::activePph).reversed()
                .thenComparing(OperatorVelocity::totalPicks).reversed());
        return result;
    }

    private Map<UUID, String> loadDisplayNames(UUID tenantId, Iterable<UUID> userIds) {
        List<UUID> ids = new ArrayList<>();
        userIds.forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Result<Record> rows = dsl.fetch("""
                SELECT id, display_name
                FROM users
                WHERE tenant_id = ? AND id = ANY (?::uuid[])
                """, tenantId, (Object) ids.toArray(UUID[]::new));
        Map<UUID, String> names = new HashMap<>();
        for (Record row : rows) {
            names.put(row.get("id", UUID.class), row.get("display_name", String.class));
        }
        return names;
    }

    private Map<UUID, List<HourlyPoint>> loadHourlyPicks(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to, Iterable<UUID> userIds) {
        List<UUID> ids = new ArrayList<>();
        userIds.forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Result<Record> rows = dsl.fetch("""
                SELECT pb.assigned_user_id AS user_id,
                       date_trunc('hour', pt.updated_at AT TIME ZONE 'UTC') AS hour_bucket,
                       COUNT(*)::bigint AS picks
                FROM picking_tasks pt
                JOIN picking_batches pb ON pb.id = pt.batch_id AND pb.tenant_id = pt.tenant_id
                WHERE pt.tenant_id = ?
                  AND pt.status = 'PICKED'
                  AND pb.assigned_user_id = ANY (?::uuid[])
                  AND pt.updated_at >= ?::timestamptz AND pt.updated_at < ?::timestamptz
                GROUP BY pb.assigned_user_id, hour_bucket
                ORDER BY hour_bucket
                """, tenantId, (Object) ids.toArray(UUID[]::new), from, to);

        // Fallback: SHIP events by hour when no wave tasks
        Result<Record> shipHours = dsl.fetch("""
                SELECT il.created_by AS user_id,
                       date_trunc('hour', il.created_at AT TIME ZONE 'UTC') AS hour_bucket,
                       COUNT(*)::bigint AS picks
                FROM inventory_ledger il
                WHERE il.tenant_id = ?
                  AND il.created_by = ANY (?::uuid[])
                  AND il.movement_type IN ('SHIP', 'PICK')
                  AND il.created_at >= ?::timestamptz AND il.created_at < ?::timestamptz
                GROUP BY il.created_by, hour_bucket
                ORDER BY hour_bucket
                """, tenantId, (Object) ids.toArray(UUID[]::new), from, to);

        Map<UUID, Map<Instant, Long>> nested = new HashMap<>();
        for (Record row : rows) {
            nested.computeIfAbsent(row.get("user_id", UUID.class), k -> new LinkedHashMap<>())
                    .merge(toInstant(row.get("hour_bucket")), row.get("picks", Long.class), Long::sum);
        }
        for (Record row : shipHours) {
            UUID userId = row.get("user_id", UUID.class);
            // Prefer wave-task hourly series when present for the operator
            if (nested.containsKey(userId) && !nested.get(userId).isEmpty()) {
                continue;
            }
            nested.computeIfAbsent(userId, k -> new LinkedHashMap<>())
                    .merge(toInstant(row.get("hour_bucket")), row.get("picks", Long.class), Long::sum);
        }

        Map<UUID, List<HourlyPoint>> out = new HashMap<>();
        for (Map.Entry<UUID, Map<Instant, Long>> e : nested.entrySet()) {
            List<HourlyPoint> points = new ArrayList<>();
            for (Map.Entry<Instant, Long> h : e.getValue().entrySet()) {
                Instant hour = h.getKey();
                String label = hour == null ? "?"
                        : hour.atZone(ZoneOffset.UTC).toLocalTime().toString().substring(0, 5);
                points.add(new HourlyPoint(hour, label, h.getValue()));
            }
            points.sort(Comparator.comparing(p -> p.hour() == null ? Instant.EPOCH : p.hour()));
            out.put(e.getKey(), points);
        }
        return out;
    }

    private static BigDecimal divide(long picks, double hours) {
        if (hours <= 0 || picks <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(picks / hours).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundHours(double hours) {
        return BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP);
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    private static final class MutableStats {
        long totalPicks;
        long waveTaskPicks;
        long totalReceives;
        BigDecimal pickUnits = BigDecimal.ZERO;
        Instant firstAt;
        Instant lastAt;
        long activeWaveSeconds;

        void expandShift(Instant first, Instant last) {
            if (first != null && (firstAt == null || first.isBefore(firstAt))) {
                firstAt = first;
            }
            if (last != null && (lastAt == null || last.isAfter(lastAt))) {
                lastAt = last;
            }
        }

        long shiftSeconds() {
            if (firstAt == null || lastAt == null || !lastAt.isAfter(firstAt)) {
                return 0;
            }
            return Duration.between(firstAt, lastAt).getSeconds();
        }
    }

    public record HourlyPoint(Instant hour, String label, long picks) {
    }

    public record OperatorVelocity(
            UUID userId,
            String operatorName,
            long totalPicks,
            long totalReceives,
            BigDecimal activePph,
            BigDecimal shiftPph,
            BigDecimal utilizationPercent,
            BigDecimal activeWaveHours,
            BigDecimal shiftHours,
            List<HourlyPoint> hourlyPicks
    ) {
    }
}

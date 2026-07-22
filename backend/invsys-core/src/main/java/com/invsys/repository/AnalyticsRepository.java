package com.invsys.repository;

import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class AnalyticsRepository {

    private final DSLContext dsl;

    public AnalyticsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record ValuationLine(
            UUID variantId,
            UUID locationId,
            BigDecimal quantityOnHand,
            BigDecimal totalValue
    ) {
    }

    public record ValuationSnapshot(
            Instant asOfDate,
            BigDecimal totalValue,
            List<ValuationLine> lines
    ) {
    }

    public ValuationSnapshot valuationAsOf(Instant asOfDate) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                SELECT variant_id,
                       location_id,
                       COALESCE(SUM(quantity_delta), 0) AS qty,
                       COALESCE(SUM(quantity_delta * COALESCE(unit_cost, 0)), 0) AS total_value
                FROM inventory_ledger
                WHERE tenant_id = ?
                  AND created_at <= CAST(? AS timestamptz)
                GROUP BY variant_id, location_id
                HAVING COALESCE(SUM(quantity_delta), 0) <> 0
                ORDER BY variant_id, location_id
                """, tenantId, asOfDate.toString());

        List<ValuationLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Record row : rows) {
            BigDecimal qty = row.get("qty", BigDecimal.class);
            BigDecimal value = row.get("total_value", BigDecimal.class);
            total = total.add(value);
            lines.add(new ValuationLine(
                    row.get("variant_id", UUID.class),
                    row.get("location_id", UUID.class),
                    qty,
                    value
            ));
        }
        return new ValuationSnapshot(asOfDate, total, lines);
    }

    public List<ValuationSnapshot> valuationHistory(Instant from, Instant to, int buckets) {
        int n = Math.min(Math.max(buckets, 2), 90);
        long spanMs = Math.max(1L, to.toEpochMilli() - from.toEpochMilli());
        List<ValuationSnapshot> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long offset = (spanMs * i) / (n - 1);
            Instant asOf = Instant.ofEpochMilli(from.toEpochMilli() + offset);
            points.add(valuationAsOf(asOf));
        }
        return points;
    }
}

package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Algorithmic directed put-away: prefer consolidating onto existing positive stock,
 * otherwise utilize the first empty BIN under the active warehouse.
 */
@Service
public class PutAwaySuggestionService {

    private final DSLContext dsl;

    public PutAwaySuggestionService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public PutAwaySuggestion suggest(UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID warehouseId = TenantContext.getWarehouseId().orElse(null);

        Result<Record> consolidation = dsl.fetch("""
                SELECT il.location_id AS location_id, l.path AS path, l.code AS code
                FROM inventory_levels il
                JOIN locations l ON l.id = il.location_id AND l.tenant_id = il.tenant_id
                WHERE il.tenant_id = ?
                  AND il.variant_id = ?
                  AND il.on_hand > 0
                ORDER BY il.on_hand DESC, l.path ASC
                LIMIT 1
                """, tenantId, variantId);
        if (!consolidation.isEmpty()) {
            Record row = consolidation.getFirst();
            return new PutAwaySuggestion(
                    row.get("location_id", UUID.class),
                    row.get("path", String.class),
                    row.get("code", String.class),
                    "CONSOLIDATION");
        }

        Result<Record> emptyBin;
        if (warehouseId != null) {
            emptyBin = dsl.fetch("""
                    SELECT l.id AS location_id, l.path AS path, l.code AS code
                    FROM locations l
                    JOIN locations wh ON wh.id = ? AND wh.tenant_id = l.tenant_id
                    WHERE l.tenant_id = ?
                      AND l.type = 'BIN'
                      AND (l.path = wh.path OR l.path LIKE wh.path || '/%')
                      AND NOT EXISTS (
                          SELECT 1 FROM inventory_levels il
                          WHERE il.tenant_id = l.tenant_id
                            AND il.location_id = l.id
                            AND (il.on_hand > 0 OR il.allocated > 0)
                      )
                    ORDER BY l.path ASC, l.code ASC
                    LIMIT 1
                    """, warehouseId, tenantId);
        } else {
            emptyBin = dsl.fetch("""
                    SELECT l.id AS location_id, l.path AS path, l.code AS code
                    FROM locations l
                    WHERE l.tenant_id = ?
                      AND l.type = 'BIN'
                      AND NOT EXISTS (
                          SELECT 1 FROM inventory_levels il
                          WHERE il.tenant_id = l.tenant_id
                            AND il.location_id = l.id
                            AND (il.on_hand > 0 OR il.allocated > 0)
                      )
                    ORDER BY l.path ASC, l.code ASC
                    LIMIT 1
                    """, tenantId);
        }

        if (emptyBin.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_PUTAWAY_LOCATION",
                    "No consolidation or empty BIN location available for put-away");
        }
        Record row = emptyBin.getFirst();
        return new PutAwaySuggestion(
                row.get("location_id", UUID.class),
                row.get("path", String.class),
                row.get("code", String.class),
                "EMPTY_BIN");
    }

    public record PutAwaySuggestion(UUID locationId, String path, String code, String strategy) {
    }
}

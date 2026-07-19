package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Directed putaway: consolidation → velocity (A near dock) → cold/hazmat zone → empty BIN.
 */
@Service
public class PutawayStrategyService {

    private final DSLContext dsl;
    private final ProductVariantRepository variantRepository;

    public PutawayStrategyService(DSLContext dsl, ProductVariantRepository variantRepository) {
        this.dsl = dsl;
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    public PutawayDirective suggest(UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID warehouseId = TenantContext.getWarehouseId().orElse(null);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));

        String tempZone = normalizeTemp(variant.getStorageTempZone());
        boolean hazmat = variant.isHazmat();
        boolean velocityA = "A".equalsIgnoreCase(variant.getAbcClassification());

        Record consolidation = fetchConsolidation(tenantId, variantId, tempZone, hazmat);
        if (consolidation != null) {
            return toDirective(consolidation, "CONSOLIDATION",
                    "Consolidate onto existing partial bin for this SKU");
        }

        if (needsCold(tempZone)) {
            Record cold = fetchEmptyBin(tenantId, warehouseId, tempZone, hazmat, false);
            if (cold != null) {
                return toDirective(cold, "COLD_ZONE",
                        "Refrigerated / frozen item — put away in cooler zone");
            }
        }

        if (hazmat) {
            Record haz = fetchEmptyBin(tenantId, warehouseId, tempZone, true, false);
            if (haz != null) {
                return toDirective(haz, "HAZMAT_ZONE",
                        "Hazmat item — put away in hazmat-approved bin");
            }
        }

        if (velocityA) {
            Record nearDock = fetchEmptyBin(tenantId, warehouseId, tempZone, hazmat, true);
            if (nearDock != null) {
                return toDirective(nearDock, "VELOCITY_DOCK",
                        "A-velocity SKU — put away in empty bin closest to shipping dock");
            }
        }

        Record empty = fetchEmptyBin(tenantId, warehouseId, tempZone, hazmat, false);
        if (empty == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_PUTAWAY_LOCATION",
                    "No consolidation or empty BIN location available for put-away");
        }
        return toDirective(empty, "EMPTY_BIN", "Put away into empty bin");
    }

    private Record fetchConsolidation(UUID tenantId, UUID variantId, String tempZone, boolean hazmat) {
        Result<Record> rows = dsl.fetch("""
                SELECT il.location_id AS location_id, l.path AS path, l.code AS code,
                       l.storage_temp_zone AS storage_temp_zone, l.allows_hazmat AS allows_hazmat
                FROM inventory_levels il
                JOIN locations l ON l.id = il.location_id AND l.tenant_id = il.tenant_id
                WHERE il.tenant_id = ?
                  AND il.variant_id = ?
                  AND il.on_hand > 0
                  AND l.type = 'BIN'
                  AND UPPER(l.storage_temp_zone) = ?
                  AND (? = FALSE OR l.allows_hazmat = TRUE)
                ORDER BY il.on_hand ASC, l.path ASC
                LIMIT 1
                """, tenantId, variantId, tempZone, hazmat);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Record fetchEmptyBin(UUID tenantId, UUID warehouseId, String tempZone, boolean hazmat,
                                 boolean nearDock) {
        String orderBy = nearDock
                ? """
                  ORDER BY
                    CASE WHEN UPPER(COALESCE(l.zone_behavior, 'STANDARD')) IN ('RECEIVING', 'PICK_FACE')
                         THEN 0 ELSE 1 END,
                    COALESCE(
                      (COALESCE(l.coord_x, 0) - COALESCE(dock.dock_x, 0))
                        * (COALESCE(l.coord_x, 0) - COALESCE(dock.dock_x, 0))
                      + (COALESCE(l.coord_y, 0) - COALESCE(dock.dock_y, 0))
                        * (COALESCE(l.coord_y, 0) - COALESCE(dock.dock_y, 0)),
                      999999999),
                    l.sequence_index ASC,
                    l.path ASC
                  """
                : "ORDER BY l.path ASC, l.code ASC";

        String sql;
        if (warehouseId != null) {
            sql = """
                    SELECT l.id AS location_id, l.path AS path, l.code AS code,
                           l.storage_temp_zone AS storage_temp_zone, l.allows_hazmat AS allows_hazmat
                    FROM locations l
                    JOIN locations wh ON wh.id = ? AND wh.tenant_id = l.tenant_id
                    LEFT JOIN LATERAL (
                        SELECT AVG(d.coord_x) AS dock_x, AVG(d.coord_y) AS dock_y
                        FROM locations d
                        WHERE d.tenant_id = l.tenant_id
                          AND (d.path = wh.path OR d.path LIKE wh.path || '/%')
                          AND (
                               UPPER(COALESCE(d.zone_behavior, '')) = 'RECEIVING'
                               OR UPPER(d.code) LIKE '%DOCK%'
                               OR UPPER(d.code) LIKE '%SHIP%'
                          )
                    ) dock ON TRUE
                    WHERE l.tenant_id = ?
                      AND l.type = 'BIN'
                      AND (l.path = wh.path OR l.path LIKE wh.path || '/%')
                      AND UPPER(l.storage_temp_zone) = ?
                      AND (? = FALSE OR l.allows_hazmat = TRUE)
                      AND NOT EXISTS (
                          SELECT 1 FROM inventory_levels il
                          WHERE il.tenant_id = l.tenant_id
                            AND il.location_id = l.id
                            AND (il.on_hand > 0 OR il.allocated > 0)
                      )
                    """ + orderBy + " LIMIT 1";
            Result<Record> rows = dsl.fetch(sql, warehouseId, tenantId, tempZone, hazmat);
            return rows.isEmpty() ? null : rows.getFirst();
        }

        sql = """
                SELECT l.id AS location_id, l.path AS path, l.code AS code,
                       l.storage_temp_zone AS storage_temp_zone, l.allows_hazmat AS allows_hazmat
                FROM locations l
                LEFT JOIN LATERAL (
                    SELECT AVG(d.coord_x) AS dock_x, AVG(d.coord_y) AS dock_y
                    FROM locations d
                    WHERE d.tenant_id = l.tenant_id
                      AND (
                           UPPER(COALESCE(d.zone_behavior, '')) = 'RECEIVING'
                           OR UPPER(d.code) LIKE '%DOCK%'
                           OR UPPER(d.code) LIKE '%SHIP%'
                      )
                ) dock ON TRUE
                WHERE l.tenant_id = ?
                  AND l.type = 'BIN'
                  AND UPPER(l.storage_temp_zone) = ?
                  AND (? = FALSE OR l.allows_hazmat = TRUE)
                  AND NOT EXISTS (
                      SELECT 1 FROM inventory_levels il
                      WHERE il.tenant_id = l.tenant_id
                        AND il.location_id = l.id
                        AND (il.on_hand > 0 OR il.allocated > 0)
                  )
                """ + orderBy + " LIMIT 1";
        Result<Record> rows = dsl.fetch(sql, tenantId, tempZone, hazmat);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static PutawayDirective toDirective(Record row, String strategy, String instruction) {
        String path = row.get("path", String.class);
        String code = row.get("code", String.class);
        return new PutawayDirective(
                row.get("location_id", UUID.class),
                path,
                code,
                strategy,
                instruction,
                parseAisle(path),
                parseRack(path),
                code);
    }

    private static String parseAisle(String path) {
        if (path == null) return null;
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.toUpperCase(Locale.ROOT).startsWith("A") || part.toUpperCase(Locale.ROOT).contains("AISLE")) {
                return part;
            }
        }
        return parts.length >= 3 ? parts[parts.length - 2] : null;
    }

    private static String parseRack(String path) {
        if (path == null) return null;
        String[] parts = path.split("/");
        return parts.length >= 2 ? parts[parts.length - 2] : null;
    }

    private static boolean needsCold(String tempZone) {
        return "REFRIGERATED".equals(tempZone) || "FROZEN".equals(tempZone);
    }

    private static String normalizeTemp(String raw) {
        if (raw == null || raw.isBlank()) {
            return "AMBIENT";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public record PutawayDirective(
            UUID locationId,
            String path,
            String code,
            String strategy,
            String instruction,
            String aisle,
            String rack,
            String binLabel
    ) {
    }
}

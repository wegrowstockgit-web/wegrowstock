package com.invsys.service;

import com.invsys.api.dto.ReplenishmentTaskDto;
import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Algorithmic internal restocking: PICK_FACE bins below min → suggest TRANSFER from RESERVE.
 */
@Service
public class ReplenishmentService {

    private final DSLContext dsl;

    public ReplenishmentService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentTaskDto> listSuggestedTransfers() {
        UUID tenantId = TenantContext.requireTenantId();

        Result<Record> breaches = dsl.fetch("""
                SELECT r.id AS rule_id,
                       r.variant_id,
                       r.location_id AS pick_face_id,
                       r.min_quantity,
                       r.max_quantity,
                       COALESCE(SUM(il.on_hand), 0) AS on_hand,
                       pv.sku,
                       COALESCE(p.name, pv.sku) AS variant_name,
                       loc.code AS pick_code,
                       loc.path AS pick_path
                FROM bin_replenishment_rules r
                JOIN locations loc
                  ON loc.id = r.location_id
                 AND loc.tenant_id = r.tenant_id
                 AND loc.zone_behavior = 'PICK_FACE'
                JOIN product_variants pv ON pv.id = r.variant_id AND pv.tenant_id = r.tenant_id
                LEFT JOIN products p ON p.id = pv.product_id AND p.tenant_id = r.tenant_id
                LEFT JOIN inventory_levels il
                       ON il.tenant_id = r.tenant_id
                      AND il.variant_id = r.variant_id
                      AND il.location_id = r.location_id
                WHERE r.tenant_id = ?
                GROUP BY r.id, r.variant_id, r.location_id, r.min_quantity, r.max_quantity,
                         pv.sku, p.name, loc.code, loc.path
                HAVING COALESCE(SUM(il.on_hand), 0) < r.min_quantity
                ORDER BY loc.path, pv.sku
                """, tenantId);

        List<ReplenishmentTaskDto> tasks = new ArrayList<>();
        for (Record breach : breaches) {
            UUID variantId = breach.get("variant_id", UUID.class);
            UUID pickFaceId = breach.get("pick_face_id", UUID.class);
            BigDecimal onHand = breach.get("on_hand", BigDecimal.class);
            BigDecimal minQty = breach.get("min_quantity", BigDecimal.class);
            BigDecimal maxQty = breach.get("max_quantity", BigDecimal.class);
            BigDecimal targetFill = maxQty.subtract(onHand);
            if (targetFill.signum() <= 0) {
                targetFill = minQty.subtract(onHand);
            }
            if (targetFill.signum() <= 0) {
                continue;
            }

            Record reserve = dsl.fetchOne("""
                    SELECT il.location_id,
                           il.lot_id,
                           il.on_hand - il.allocated AS available,
                           loc.code AS reserve_code,
                           loc.path AS reserve_path,
                           lot.lot_number
                    FROM inventory_levels il
                    JOIN locations loc
                      ON loc.id = il.location_id
                     AND loc.tenant_id = il.tenant_id
                     AND loc.zone_behavior = 'RESERVE'
                    LEFT JOIN lots lot ON lot.id = il.lot_id
                    WHERE il.tenant_id = ?
                      AND il.variant_id = ?
                      AND il.location_id <> ?
                      AND (il.on_hand - il.allocated) > 0
                    ORDER BY (il.on_hand - il.allocated) DESC, loc.path
                    LIMIT 1
                    """, tenantId, variantId, pickFaceId);

            if (reserve == null) {
                continue;
            }

            BigDecimal available = reserve.get("available", BigDecimal.class);
            if (available == null || available.signum() <= 0) {
                continue;
            }
            BigDecimal suggested = available.min(targetFill);
            String sku = breach.get("sku", String.class);
            String fromCode = reserve.get("reserve_code", String.class);
            String toCode = breach.get("pick_code", String.class);
            String instruction = "Move " + suggested.stripTrailingZeros().toPlainString()
                    + " of " + sku
                    + " from " + fromCode
                    + " to " + toCode;

            tasks.add(new ReplenishmentTaskDto(
                    breach.get("rule_id", UUID.class),
                    variantId,
                    sku,
                    breach.get("variant_name", String.class),
                    reserve.get("lot_id", UUID.class),
                    reserve.get("lot_number", String.class),
                    reserve.get("location_id", UUID.class),
                    fromCode,
                    reserve.get("reserve_path", String.class),
                    pickFaceId,
                    toCode,
                    breach.get("pick_path", String.class),
                    onHand,
                    minQty,
                    maxQty,
                    suggested,
                    instruction));
        }
        return tasks;
    }
}

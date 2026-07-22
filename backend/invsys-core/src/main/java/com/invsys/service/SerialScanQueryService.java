package com.invsys.service;

import com.invsys.api.dto.SerialScanResponse;
import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class SerialScanQueryService {

    private final DSLContext dsl;

    public SerialScanQueryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public SerialScanResponse lookup(String serialNumber) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                SELECT sn.id AS serial_id,
                       sn.serial_number,
                       sn.variant_id,
                       sn.status,
                       pv.sku,
                       p.name AS product_name,
                       loc.id AS location_id,
                       loc.path AS location_path
                FROM serial_numbers sn
                JOIN product_variants pv ON pv.id = sn.variant_id AND pv.tenant_id = sn.tenant_id
                JOIN products p ON p.id = pv.product_id AND p.tenant_id = sn.tenant_id
                LEFT JOIN LATERAL (
                    SELECT il.location_id
                    FROM inventory_ledger il
                    WHERE il.serial_number_id = sn.id
                      AND il.tenant_id = sn.tenant_id
                    ORDER BY il.created_at DESC
                    LIMIT 1
                ) latest ON TRUE
                LEFT JOIN locations loc ON loc.id = latest.location_id AND loc.tenant_id = sn.tenant_id
                WHERE sn.tenant_id = ?
                  AND sn.serial_number = ?
                """, tenantId, serialNumber);

        if (rows.isEmpty()) {
            return null;
        }
        Record row = rows.getFirst();
        return new SerialScanResponse(
                row.get("serial_id", UUID.class),
                row.get("serial_number", String.class),
                row.get("variant_id", UUID.class),
                row.get("sku", String.class),
                row.get("product_name", String.class),
                row.get("status", String.class),
                row.get("location_id", UUID.class),
                row.get("location_path", String.class),
                BigDecimal.ONE
        );
    }
}

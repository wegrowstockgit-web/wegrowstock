package com.invsys.service;

import com.invsys.api.dto.ComplianceLotTraceResponse;
import com.invsys.api.dto.GenealogyNode;
import com.invsys.api.dto.LotTraceResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.Lot;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.LotRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryGenealogyService {

    private final DSLContext dsl;
    private final LotRepository lotRepository;
    private final ProductVariantRepository variantRepository;

    public InventoryGenealogyService(DSLContext dsl,
                                     LotRepository lotRepository,
                                     ProductVariantRepository variantRepository) {
        this.dsl = dsl;
        this.lotRepository = lotRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    public LotTraceResponse traceByLotId(UUID lotId) {
        return buildTrace(requireLot(lotId));
    }

    @Transactional(readOnly = true)
    public LotTraceResponse traceByLotNumber(String lotNumber) {
        return buildTrace(requireLotByNumber(lotNumber));
    }

    /**
     * Multi-directional compliance payload: origin RECEIVE + PO, live bin exposure, SHIP + SO.
     */
    @Transactional(readOnly = true)
    public ComplianceLotTraceResponse complianceTrace(UUID lotId, String lotNumber) {
        Lot lot = lotId != null ? requireLot(lotId) : requireLotByNumber(lotNumber);
        ProductVariant variant = variantRepository.findById(lot.getVariantId())
                .filter(v -> v.getTenantId().equals(TenantContext.requireTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        return new ComplianceLotTraceResponse(
                lot.getId(),
                lot.getLotNumber(),
                variant.getId(),
                variant.getSku(),
                loadOrigin(lot.getId()),
                loadExposure(lot.getId()),
                loadDownstreamShipments(lot.getId()));
    }

    private Lot requireLot(UUID lotId) {
        return lotRepository.findById(lotId)
                .filter(l -> l.getTenantId().equals(TenantContext.requireTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Lot not found"));
    }

    private Lot requireLotByNumber(String lotNumber) {
        if (lotNumber == null || lotNumber.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "lotId or lotNumber is required");
        }
        UUID tenantId = TenantContext.requireTenantId();
        return lotRepository.findFirstByTenantIdAndLotNumber(tenantId, lotNumber.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Lot not found"));
    }

    private LotTraceResponse buildTrace(Lot lot) {
        return new LotTraceResponse(
                lot.getId(),
                lot.getLotNumber(),
                upstreamTrace(lot.getId()),
                downstreamRecall(lot.getId()));
    }

    private ComplianceLotTraceResponse.LotOrigin loadOrigin(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        Record row = dsl.fetchOne("""
                SELECT il.id, il.quantity_delta, il.created_at, il.location_id,
                       loc.code AS location_code, loc.path AS location_path,
                       pol.id AS po_line_id, po.id AS po_id, po.number AS po_number,
                       s.id AS supplier_id, s.name AS supplier_name
                FROM inventory_ledger il
                LEFT JOIN locations loc ON loc.id = il.location_id
                LEFT JOIN purchase_order_lines pol
                       ON il.reference_type = 'PURCHASE_ORDER_LINE' AND pol.id = il.reference_id
                LEFT JOIN purchase_orders po ON po.id = pol.purchase_order_id
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                WHERE il.tenant_id = ? AND il.lot_id = ? AND il.movement_type = 'RECEIVE'
                ORDER BY il.created_at ASC
                LIMIT 1
                """, tenantId, lotId);
        if (row == null) {
            return null;
        }
        return new ComplianceLotTraceResponse.LotOrigin(
                row.get("id", UUID.class),
                toInstant(row.get("created_at")),
                row.get("quantity_delta", BigDecimal.class),
                row.get("location_id", UUID.class),
                row.get("location_code", String.class),
                row.get("location_path", String.class),
                row.get("po_id", UUID.class),
                row.get("po_number", String.class),
                row.get("po_line_id", UUID.class),
                row.get("supplier_id", UUID.class),
                row.get("supplier_name", String.class));
    }

    private List<ComplianceLotTraceResponse.LotExposure> loadExposure(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                SELECT il.id, il.location_id, il.on_hand, il.allocated,
                       loc.code AS location_code, loc.path AS location_path,
                       loc.type AS location_type, loc.zone_behavior
                FROM inventory_levels il
                JOIN locations loc ON loc.id = il.location_id
                WHERE il.tenant_id = ? AND il.lot_id = ? AND il.on_hand > 0
                ORDER BY loc.path
                """, tenantId, lotId);
        List<ComplianceLotTraceResponse.LotExposure> out = new ArrayList<>();
        for (Record row : rows) {
            BigDecimal onHand = row.get("on_hand", BigDecimal.class);
            BigDecimal allocated = row.get("allocated", BigDecimal.class);
            if (onHand == null) {
                onHand = BigDecimal.ZERO;
            }
            if (allocated == null) {
                allocated = BigDecimal.ZERO;
            }
            out.add(new ComplianceLotTraceResponse.LotExposure(
                    row.get("id", UUID.class),
                    row.get("location_id", UUID.class),
                    row.get("location_code", String.class),
                    row.get("location_path", String.class),
                    row.get("location_type", String.class),
                    row.get("zone_behavior", String.class),
                    onHand,
                    allocated,
                    onHand.subtract(allocated).max(BigDecimal.ZERO)));
        }
        return out;
    }

    private List<ComplianceLotTraceResponse.LotDownstreamShipment> loadDownstreamShipments(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                SELECT il.id, il.quantity_delta, il.created_at,
                       sol.id AS so_line_id, so.id AS so_id, so.number AS so_number,
                       c.id AS customer_id, c.name AS customer_name,
                       sh.id AS shipment_id, sh.tracking_number
                FROM inventory_ledger il
                LEFT JOIN sales_order_lines sol
                       ON il.reference_type = 'SALES_ORDER_LINE' AND sol.id = il.reference_id
                LEFT JOIN sales_orders so ON so.id = sol.sales_order_id
                LEFT JOIN customers c ON c.id = so.customer_id
                LEFT JOIN LATERAL (
                    SELECT s.id, s.tracking_number
                    FROM shipments s
                    WHERE s.tenant_id = il.tenant_id AND s.sales_order_id = so.id
                    ORDER BY s.created_at DESC
                    LIMIT 1
                ) sh ON TRUE
                WHERE il.tenant_id = ? AND il.lot_id = ? AND il.movement_type = 'SHIP'
                ORDER BY il.created_at
                """, tenantId, lotId);
        List<ComplianceLotTraceResponse.LotDownstreamShipment> out = new ArrayList<>();
        for (Record row : rows) {
            BigDecimal qty = row.get("quantity_delta", BigDecimal.class);
            if (qty != null) {
                qty = qty.abs();
            }
            out.add(new ComplianceLotTraceResponse.LotDownstreamShipment(
                    row.get("id", UUID.class),
                    toInstant(row.get("created_at")),
                    qty,
                    row.get("so_id", UUID.class),
                    row.get("so_number", String.class),
                    row.get("so_line_id", UUID.class),
                    row.get("customer_id", UUID.class),
                    row.get("customer_name", String.class),
                    row.get("shipment_id", UUID.class),
                    row.get("tracking_number", String.class)));
        }
        return out;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        return Instant.parse(value.toString());
    }

    @Transactional(readOnly = true)
    public GenealogyNode upstreamTrace(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                WITH RECURSIVE upstream AS (
                    SELECT il.id, il.movement_type, il.quantity_delta, il.reference_type, il.reference_id,
                           il.transfer_group_id, il.location_id, il.created_at, 0 AS depth
                    FROM inventory_ledger il
                    WHERE il.tenant_id = ? AND il.lot_id = ?
                      AND il.movement_type IN ('RECEIVE', 'TRANSFER_IN', 'ASSEMBLY_IN')
                    UNION ALL
                    SELECT prev.id, prev.movement_type, prev.quantity_delta, prev.reference_type, prev.reference_id,
                           prev.transfer_group_id, prev.location_id, prev.created_at, u.depth + 1
                    FROM upstream u
                    JOIN inventory_ledger prev
                      ON prev.tenant_id = ?
                     AND prev.lot_id = ?
                     AND prev.transfer_group_id = u.transfer_group_id
                     AND prev.movement_type = 'TRANSFER_OUT'
                     AND u.movement_type = 'TRANSFER_IN'
                    WHERE u.depth < 20
                )
                SELECT DISTINCT ON (u.id)
                       u.id, u.movement_type, u.quantity_delta, u.reference_type, u.reference_id,
                       u.transfer_group_id, u.location_id, u.created_at, u.depth,
                       loc.code AS location_code,
                       pol.id AS po_line_id, po.number AS po_number, s.name AS supplier_name,
                       prod.number AS production_number
                FROM upstream u
                LEFT JOIN locations loc ON loc.id = u.location_id
                LEFT JOIN purchase_order_lines pol
                       ON u.reference_type = 'PURCHASE_ORDER_LINE' AND pol.id = u.reference_id
                LEFT JOIN purchase_orders po ON po.id = pol.purchase_order_id
                LEFT JOIN suppliers s ON s.id = po.supplier_id
                LEFT JOIN production_orders prod
                       ON u.reference_type = 'PRODUCTION_ORDER' AND prod.id = u.reference_id
                ORDER BY u.id, u.depth
                """, tenantId, lotId, tenantId, lotId);

        List<GenealogyNode> children = new ArrayList<>();
        for (Record row : rows) {
            children.add(mapUpstreamNode(row));
        }
        return new GenealogyNode(
                "upstream-" + lotId,
                "UPSTREAM",
                "Upstream origins",
                "Receipts, transfers, and assemblies for this lot",
                children);
    }

    @Transactional(readOnly = true)
    public GenealogyNode downstreamRecall(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                SELECT il.id, il.movement_type, il.quantity_delta, il.reference_type, il.reference_id,
                       il.location_id, il.created_at,
                       loc.code AS location_code,
                       sol.id AS so_line_id, so.number AS so_number, c.name AS customer_name,
                       sh.id AS shipment_id, sh.tracking_number AS shipment_number,
                       prod.number AS production_number
                FROM inventory_ledger il
                LEFT JOIN locations loc ON loc.id = il.location_id
                LEFT JOIN sales_order_lines sol
                       ON il.reference_type = 'SALES_ORDER_LINE' AND sol.id = il.reference_id
                LEFT JOIN sales_orders so ON so.id = sol.sales_order_id
                LEFT JOIN customers c ON c.id = so.customer_id
                LEFT JOIN shipments sh ON sh.sales_order_id = so.id
                LEFT JOIN production_orders prod
                       ON il.reference_type = 'PRODUCTION_ORDER' AND prod.id = il.reference_id
                WHERE il.tenant_id = ? AND il.lot_id = ?
                  AND il.movement_type IN ('SHIP', 'ASSEMBLY_OUT')
                ORDER BY il.created_at
                """, tenantId, lotId);

        List<GenealogyNode> children = new ArrayList<>();
        for (Record row : rows) {
            children.add(mapDownstreamNode(row));
        }
        return new GenealogyNode(
                "downstream-" + lotId,
                "DOWNSTREAM",
                "Downstream destinations",
                "Shipments and assembly consumption for this lot",
                children);
    }

    private GenealogyNode mapUpstreamNode(Record row) {
        String type = row.get("movement_type", String.class);
        String locationCode = row.get("location_code", String.class);
        String detail;
        String label;
        if ("RECEIVE".equals(type)) {
            String supplier = row.get("supplier_name", String.class);
            String poNumber = row.get("po_number", String.class);
            label = "Receive" + (poNumber != null ? " · PO " + poNumber : "");
            detail = (supplier != null ? "Supplier: " + supplier + " · " : "")
                    + "Loc " + nullSafe(locationCode)
                    + " · qty " + row.get("quantity_delta");
        } else if ("ASSEMBLY_IN".equals(type)) {
            String prod = row.get("production_number", String.class);
            label = "Assembly in" + (prod != null ? " · " + prod : "");
            detail = "Loc " + nullSafe(locationCode) + " · qty " + row.get("quantity_delta");
        } else {
            label = "Transfer in · " + nullSafe(locationCode);
            detail = "Group " + row.get("transfer_group_id") + " · qty " + row.get("quantity_delta");
        }
        return new GenealogyNode(
                row.get("id", UUID.class).toString(),
                type,
                label,
                detail,
                List.of());
    }

    private GenealogyNode mapDownstreamNode(Record row) {
        String type = row.get("movement_type", String.class);
        List<GenealogyNode> children = new ArrayList<>();
        String label;
        String detail;
        if ("SHIP".equals(type)) {
            String soNumber = row.get("so_number", String.class);
            String customer = row.get("customer_name", String.class);
            label = "Ship" + (soNumber != null ? " · SO " + soNumber : "");
            detail = (customer != null ? "Customer: " + customer + " · " : "")
                    + "qty " + row.get("quantity_delta");
            if (customer != null) {
                children.add(new GenealogyNode(
                        "customer-" + row.get("id"),
                        "CUSTOMER",
                        customer,
                        soNumber != null ? "Order " + soNumber : null,
                        List.of()));
            }
            String shipmentNumber = row.get("shipment_number", String.class);
            if (shipmentNumber != null) {
                children.add(new GenealogyNode(
                        "shipment-" + row.get("shipment_id"),
                        "SHIPMENT",
                        "Shipment " + shipmentNumber,
                        null,
                        List.of()));
            }
        } else {
            String prod = row.get("production_number", String.class);
            label = "Assembly out" + (prod != null ? " · " + prod : "");
            detail = "qty " + row.get("quantity_delta");
        }
        return new GenealogyNode(
                row.get("id", UUID.class).toString(),
                type,
                label,
                detail,
                children);
    }

    private static String nullSafe(String value) {
        return value != null ? value : "?";
    }
}

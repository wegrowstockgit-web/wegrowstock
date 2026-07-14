package com.invsys.service;

import com.invsys.api.dto.GenealogyNode;
import com.invsys.api.dto.LotTraceResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.Lot;
import com.invsys.repository.LotRepository;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryGenealogyService {

    private final DSLContext dsl;
    private final LotRepository lotRepository;

    public InventoryGenealogyService(DSLContext dsl, LotRepository lotRepository) {
        this.dsl = dsl;
        this.lotRepository = lotRepository;
    }

    @Transactional(readOnly = true)
    public LotTraceResponse traceByLotId(UUID lotId) {
        Lot lot = lotRepository.findById(lotId)
                .filter(l -> l.getTenantId().equals(TenantContext.requireTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Lot not found"));
        return buildTrace(lot);
    }

    @Transactional(readOnly = true)
    public LotTraceResponse traceByLotNumber(String lotNumber) {
        UUID tenantId = TenantContext.requireTenantId();
        Lot lot = lotRepository.findFirstByTenantIdAndLotNumber(tenantId, lotNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Lot not found"));
        return buildTrace(lot);
    }

    private LotTraceResponse buildTrace(Lot lot) {
        return new LotTraceResponse(
                lot.getId(),
                lot.getLotNumber(),
                upstreamTrace(lot.getId()),
                downstreamRecall(lot.getId()));
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

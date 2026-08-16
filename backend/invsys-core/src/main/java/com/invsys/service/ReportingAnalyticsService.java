package com.invsys.service;

import com.invsys.api.dto.CogsLedgerReport;
import com.invsys.api.dto.CogsLedgerRow;
import com.invsys.api.dto.FulfillmentSummaryReport;
import com.invsys.api.dto.InventoryValuationReport;
import com.invsys.api.dto.InventoryValuationRow;
import com.invsys.api.dto.ProfitByCustomerRow;
import com.invsys.api.dto.ProfitByProductRow;
import com.invsys.api.dto.ProfitMarginReport;
import com.invsys.api.dto.PurchaseSpendReport;
import com.invsys.api.dto.PurchaseSpendRow;
import com.invsys.api.dto.ReportChartPoint;
import com.invsys.api.dto.ReturnsAnalysisReport;
import com.invsys.api.dto.ReturnsAnalysisRow;
import com.invsys.api.dto.SalesPerformanceReport;
import com.invsys.api.dto.StockTurnoverReport;
import com.invsys.api.dto.StockTurnoverRow;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReportingAnalyticsService {

    private final DSLContext dsl;
    private final TenantSettingsRepository settingsRepository;

    public ReportingAnalyticsService(DSLContext dsl, TenantSettingsRepository settingsRepository) {
        this.dsl = dsl;
        this.settingsRepository = settingsRepository;
    }

    public InventoryValuationReport inventoryValuation() {
        UUID tenantId = TenantContext.requireTenantId();
        String currency = resolveCurrency(tenantId);

        Result<Record> rows = dsl.fetch("""
                SELECT wh.id AS warehouse_id,
                       wh.code AS warehouse_code,
                       wh.name AS warehouse_name,
                       pv.id AS variant_id,
                       pv.sku,
                       p.name AS product_name,
                       COALESCE(SUM(il.on_hand), 0) AS on_hand,
                       pv.avg_cost
                FROM inventory_levels il
                JOIN locations bin ON bin.id = il.location_id AND bin.tenant_id = il.tenant_id
                JOIN locations wh ON wh.id = (
                    SELECT l2.id FROM locations l2
                    WHERE l2.tenant_id = bin.tenant_id
                      AND bin.path LIKE l2.path || '%'
                      AND l2.type = 'WAREHOUSE'
                    ORDER BY length(l2.path) DESC
                    LIMIT 1
                )
                JOIN product_variants pv ON pv.id = il.variant_id AND pv.tenant_id = il.tenant_id
                JOIN products p ON p.id = pv.product_id AND p.tenant_id = il.tenant_id
                WHERE il.tenant_id = ?
                GROUP BY wh.id, wh.code, wh.name, pv.id, pv.sku, p.name, pv.avg_cost
                HAVING COALESCE(SUM(il.on_hand), 0) <> 0
                ORDER BY wh.path, pv.sku
                """, tenantId);

        List<InventoryValuationRow> items = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (Record row : rows) {
            BigDecimal onHand = row.get("on_hand", BigDecimal.class);
            BigDecimal avgCost = row.get("avg_cost", BigDecimal.class);
            BigDecimal totalValue = onHand.multiply(avgCost).setScale(4, RoundingMode.HALF_UP);
            grandTotal = grandTotal.add(totalValue);
            items.add(new InventoryValuationRow(
                    row.get("warehouse_id", UUID.class),
                    row.get("warehouse_code", String.class),
                    row.get("warehouse_name", String.class),
                    row.get("variant_id", UUID.class),
                    row.get("sku", String.class),
                    row.get("product_name", String.class),
                    onHand,
                    avgCost,
                    totalValue
            ));
        }
        return new InventoryValuationReport(grandTotal.setScale(4, RoundingMode.HALF_UP), currency, items);
    }

    public StockTurnoverReport stockTurnover(int periodDays) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                WITH shipped AS (
                    SELECT variant_id, SUM(ABS(quantity_delta)) AS units_shipped
                    FROM inventory_ledger
                    WHERE tenant_id = ?
                      AND movement_type = 'SHIP'
                      AND created_at >= NOW() - (? || ' days')::interval
                    GROUP BY variant_id
                ),
                avg_stock AS (
                    SELECT variant_id, AVG(on_hand) AS average_on_hand
                    FROM inventory_levels
                    WHERE tenant_id = ?
                    GROUP BY variant_id
                )
                SELECT pv.id AS variant_id,
                       pv.sku,
                       p.name AS product_name,
                       COALESCE(s.units_shipped, 0) AS units_shipped,
                       COALESCE(a.average_on_hand, 0) AS average_on_hand
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.tenant_id = pv.tenant_id
                LEFT JOIN shipped s ON s.variant_id = pv.id
                LEFT JOIN avg_stock a ON a.variant_id = pv.id
                WHERE pv.tenant_id = ?
                  AND (COALESCE(s.units_shipped, 0) > 0 OR COALESCE(a.average_on_hand, 0) > 0)
                ORDER BY pv.sku
                """, tenantId, periodDays, tenantId, tenantId);

        List<StockTurnoverRow> items = new ArrayList<>();
        for (Record row : rows) {
            BigDecimal shipped = row.get("units_shipped", BigDecimal.class);
            BigDecimal avg = row.get("average_on_hand", BigDecimal.class);
            BigDecimal rate = avg.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : shipped.divide(avg, 4, RoundingMode.HALF_UP);
            items.add(new StockTurnoverRow(
                    row.get("variant_id", UUID.class),
                    row.get("sku", String.class),
                    row.get("product_name", String.class),
                    shipped,
                    avg,
                    rate
            ));
        }
        return new StockTurnoverReport(String.valueOf(periodDays), items);
    }

    public CogsLedgerReport cogsLedger() {
        UUID tenantId = TenantContext.requireTenantId();
        String currency = resolveCurrency(tenantId);

        Result<Record> rows = dsl.fetch("""
                SELECT COALESCE(so.channel, 'DIRECT') AS channel,
                       c.id AS customer_id,
                       c.name AS customer_name,
                       il.movement_type,
                       ABS(il.quantity_delta) AS quantity,
                       COALESCE(il.unit_cost, pv.avg_cost, 0) AS unit_cost
                FROM inventory_ledger il
                JOIN product_variants pv ON pv.id = il.variant_id AND pv.tenant_id = il.tenant_id
                LEFT JOIN sales_order_lines sol ON sol.id = il.reference_id
                    AND il.reference_type = 'SALES_ORDER_LINE'
                LEFT JOIN sales_orders so ON so.id = sol.sales_order_id
                LEFT JOIN customers c ON c.id = so.customer_id
                WHERE il.tenant_id = ?
                  AND il.movement_type IN ('SHIP', 'ASSEMBLY_OUT')
                ORDER BY il.created_at DESC
                """, tenantId);

        List<CogsLedgerRow> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Record row : rows) {
            BigDecimal qty = row.get("quantity", BigDecimal.class);
            BigDecimal unitCost = row.get("unit_cost", BigDecimal.class);
            BigDecimal amount = qty.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
            total = total.add(amount);
            items.add(new CogsLedgerRow(
                    row.get("channel", String.class),
                    row.get("customer_id", UUID.class),
                    row.get("customer_name", String.class),
                    row.get("movement_type", String.class),
                    qty,
                    unitCost,
                    amount
            ));
        }
        return new CogsLedgerReport(total.setScale(4, RoundingMode.HALF_UP), currency, items);
    }

    public ProfitMarginReport profitMargin(int periodDays) {
        UUID tenantId = TenantContext.requireTenantId();
        String currency = resolveCurrency(tenantId);

        Result<Record> monthlyRevenue = dsl.fetch("""
                SELECT TO_CHAR(i.created_at, 'YYYY-MM') AS period,
                       COALESCE(SUM(il.amount), 0) AS revenue
                FROM invoices i
                JOIN invoice_lines il ON il.invoice_id = i.id AND il.tenant_id = i.tenant_id
                WHERE i.tenant_id = ?
                  AND i.status IN ('OPEN', 'PAID')
                  AND i.created_at >= NOW() - (? || ' days')::interval
                GROUP BY 1
                ORDER BY 1
                """, tenantId, periodDays);

        Result<Record> monthlyCogs = dsl.fetch("""
                SELECT TO_CHAR(il.created_at, 'YYYY-MM') AS period,
                       COALESCE(SUM(ABS(il.quantity_delta) * COALESCE(il.unit_cost, pv.avg_cost, 0)), 0) AS cogs
                FROM inventory_ledger il
                JOIN product_variants pv ON pv.id = il.variant_id AND pv.tenant_id = il.tenant_id
                WHERE il.tenant_id = ?
                  AND il.movement_type IN ('SHIP', 'ASSEMBLY_OUT')
                  AND il.created_at >= NOW() - (? || ' days')::interval
                GROUP BY 1
                ORDER BY 1
                """, tenantId, periodDays);

        Map<String, BigDecimal> revenueByPeriod = new LinkedHashMap<>();
        for (Record row : monthlyRevenue) {
            revenueByPeriod.put(row.get("period", String.class), row.get("revenue", BigDecimal.class));
        }
        Map<String, BigDecimal> cogsByPeriod = new LinkedHashMap<>();
        for (Record row : monthlyCogs) {
            cogsByPeriod.put(row.get("period", String.class), row.get("cogs", BigDecimal.class));
        }

        List<ReportChartPoint> revenueByMonth = new ArrayList<>();
        List<ReportChartPoint> profitByMonth = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : revenueByPeriod.entrySet()) {
            BigDecimal revenue = entry.getValue();
            BigDecimal cogs = cogsByPeriod.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal profit = revenue.subtract(cogs);
            totalRevenue = totalRevenue.add(revenue);
            totalCogs = totalCogs.add(cogs);
            revenueByMonth.add(new ReportChartPoint(entry.getKey(), revenue));
            profitByMonth.add(new ReportChartPoint(entry.getKey(), profit));
        }

        Result<Record> productRows = dsl.fetch("""
                SELECT pv.id AS variant_id,
                       pv.sku,
                       p.name AS product_name,
                       COALESCE(SUM(sol.qty_shipped * sol.unit_price), 0) AS revenue,
                       COALESCE(SUM(sol.qty_shipped * COALESCE(pv.avg_cost, 0)), 0) AS cogs
                FROM sales_order_lines sol
                JOIN sales_orders so ON so.id = sol.sales_order_id AND so.tenant_id = sol.tenant_id
                JOIN product_variants pv ON pv.id = sol.variant_id AND pv.tenant_id = sol.tenant_id
                JOIN products p ON p.id = pv.product_id AND p.tenant_id = sol.tenant_id
                WHERE sol.tenant_id = ?
                  AND sol.qty_shipped > 0
                  AND so.created_at >= NOW() - (? || ' days')::interval
                GROUP BY pv.id, pv.sku, p.name
                ORDER BY revenue DESC
                """, tenantId, periodDays);

        List<ProfitByProductRow> byProduct = new ArrayList<>();
        for (Record row : productRows) {
            BigDecimal revenue = row.get("revenue", BigDecimal.class);
            BigDecimal cogs = row.get("cogs", BigDecimal.class);
            BigDecimal profit = revenue.subtract(cogs);
            BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
            byProduct.add(new ProfitByProductRow(
                    row.get("variant_id", UUID.class),
                    row.get("sku", String.class),
                    row.get("product_name", String.class),
                    revenue,
                    cogs,
                    profit,
                    margin
            ));
        }

        Result<Record> customerRows = dsl.fetch("""
                SELECT c.id AS customer_id,
                       c.name AS customer_name,
                       COALESCE(SUM(il.amount), 0) AS revenue,
                       COALESCE(SUM(il.amount * 0.65), 0) AS estimated_cogs
                FROM invoices i
                JOIN customers c ON c.id = i.customer_id AND c.tenant_id = i.tenant_id
                JOIN invoice_lines il ON il.invoice_id = i.id AND il.tenant_id = i.tenant_id
                WHERE i.tenant_id = ?
                  AND i.status IN ('OPEN', 'PAID')
                  AND i.created_at >= NOW() - (? || ' days')::interval
                GROUP BY c.id, c.name
                ORDER BY revenue DESC
                """, tenantId, periodDays);

        List<ProfitByCustomerRow> byCustomer = new ArrayList<>();
        for (Record row : customerRows) {
            BigDecimal revenue = row.get("revenue", BigDecimal.class);
            BigDecimal cogs = row.get("estimated_cogs", BigDecimal.class);
            BigDecimal profit = revenue.subtract(cogs);
            BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
            byCustomer.add(new ProfitByCustomerRow(
                    row.get("customer_id", UUID.class),
                    row.get("customer_name", String.class),
                    revenue,
                    cogs,
                    profit,
                    margin
            ));
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCogs);
        BigDecimal grossMarginPercent = totalRevenue.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : grossProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP);

        return new ProfitMarginReport(
                totalRevenue.setScale(2, RoundingMode.HALF_UP),
                totalCogs.setScale(2, RoundingMode.HALF_UP),
                grossProfit.setScale(2, RoundingMode.HALF_UP),
                grossMarginPercent,
                currency,
                revenueByMonth,
                profitByMonth,
                byProduct,
                byCustomer
        );
    }

    public SalesPerformanceReport salesPerformance(int periodDays) {
        UUID tenantId = TenantContext.requireTenantId();
        String currency = resolveCurrency(tenantId);

        BigDecimal totalRevenue = dsl.fetchOne("""
                SELECT COALESCE(SUM(il.amount), 0)
                FROM invoices i
                JOIN invoice_lines il ON il.invoice_id = i.id AND il.tenant_id = i.tenant_id
                WHERE i.tenant_id = ?
                  AND i.status IN ('OPEN', 'PAID')
                  AND i.created_at >= NOW() - (? || ' days')::interval
                """, tenantId, periodDays).into(BigDecimal.class);

        BigDecimal totalOrders = dsl.fetchOne("""
                SELECT COUNT(*)::numeric
                FROM sales_orders
                WHERE tenant_id = ?
                  AND created_at >= NOW() - (? || ' days')::interval
                """, tenantId, periodDays).into(BigDecimal.class);

        List<ReportChartPoint> revenueByMonth = chartPoints(dsl.fetch("""
                SELECT TO_CHAR(i.created_at, 'YYYY-MM') AS label,
                       COALESCE(SUM(il.amount), 0) AS value
                FROM invoices i
                JOIN invoice_lines il ON il.invoice_id = i.id AND il.tenant_id = i.tenant_id
                WHERE i.tenant_id = ?
                  AND i.status IN ('OPEN', 'PAID')
                  AND i.created_at >= NOW() - (? || ' days')::interval
                GROUP BY 1 ORDER BY 1
                """, tenantId, periodDays));

        List<ReportChartPoint> ordersByStatus = chartPoints(dsl.fetch("""
                SELECT status AS label, COUNT(*)::numeric AS value
                FROM sales_orders
                WHERE tenant_id = ?
                  AND created_at >= NOW() - (? || ' days')::interval
                GROUP BY status ORDER BY value DESC
                """, tenantId, periodDays));

        List<ReportChartPoint> revenueByChannel = chartPoints(dsl.fetch("""
                SELECT COALESCE(so.channel, 'DIRECT') AS label,
                       COALESCE(SUM(sol.qty_shipped * sol.unit_price), 0) AS value
                FROM sales_orders so
                JOIN sales_order_lines sol ON sol.sales_order_id = so.id AND sol.tenant_id = so.tenant_id
                WHERE so.tenant_id = ?
                  AND so.created_at >= NOW() - (? || ' days')::interval
                GROUP BY 1 ORDER BY value DESC
                """, tenantId, periodDays));

        List<ReportChartPoint> revenueByCustomer = chartPoints(dsl.fetch("""
                SELECT c.name AS label, COALESCE(SUM(il.amount), 0) AS value
                FROM invoices i
                JOIN customers c ON c.id = i.customer_id AND c.tenant_id = i.tenant_id
                JOIN invoice_lines il ON il.invoice_id = i.id AND il.tenant_id = i.tenant_id
                WHERE i.tenant_id = ?
                  AND i.status IN ('OPEN', 'PAID')
                  AND i.created_at >= NOW() - (? || ' days')::interval
                GROUP BY c.name ORDER BY value DESC
                """, tenantId, periodDays));

        return new SalesPerformanceReport(
                totalRevenue.setScale(2, RoundingMode.HALF_UP),
                totalOrders,
                currency,
                revenueByMonth,
                ordersByStatus,
                revenueByChannel,
                revenueByCustomer
        );
    }

    public FulfillmentSummaryReport fulfillmentSummary(int periodDays) {
        UUID tenantId = TenantContext.requireTenantId();

        BigDecimal unitsShipped30d = dsl.fetchOne("""
                SELECT COALESCE(SUM(ABS(quantity_delta)), 0)
                FROM inventory_ledger
                WHERE tenant_id = ?
                  AND movement_type = 'SHIP'
                  AND created_at >= NOW() - (? || ' days')::interval
                """, tenantId, periodDays).into(BigDecimal.class);

        BigDecimal openOrderLines = dsl.fetchOne("""
                SELECT COALESCE(SUM(sol.qty_ordered - sol.qty_shipped), 0)
                FROM sales_order_lines sol
                JOIN sales_orders so ON so.id = sol.sales_order_id AND so.tenant_id = sol.tenant_id
                WHERE sol.tenant_id = ?
                  AND so.status IN ('CONFIRMED', 'UNALLOCATED', 'PARTIALLY_ALLOCATED', 'ALLOCATED', 'BACKORDERED', 'PARTIALLY_SHIPPED')
                """, tenantId).into(BigDecimal.class);

        BigDecimal ordered = dsl.fetchOne("""
                SELECT COALESCE(SUM(sol.qty_ordered), 0)
                FROM sales_order_lines sol
                JOIN sales_orders so ON so.id = sol.sales_order_id AND so.tenant_id = sol.tenant_id
                WHERE sol.tenant_id = ?
                  AND so.created_at >= NOW() - (? || ' days')::interval
                """, tenantId, periodDays).into(BigDecimal.class);

        BigDecimal shipped = dsl.fetchOne("""
                SELECT COALESCE(SUM(sol.qty_shipped), 0)
                FROM sales_order_lines sol
                JOIN sales_orders so ON so.id = sol.sales_order_id AND so.tenant_id = sol.tenant_id
                WHERE sol.tenant_id = ?
                  AND so.created_at >= NOW() - (? || ' days')::interval
                """, tenantId, periodDays).into(BigDecimal.class);

        BigDecimal fillRatePercent = ordered.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : shipped.multiply(BigDecimal.valueOf(100)).divide(ordered, 2, RoundingMode.HALF_UP);

        List<ReportChartPoint> ordersByStatus = chartPoints(dsl.fetch("""
                SELECT status AS label, COUNT(*)::numeric AS value
                FROM sales_orders
                WHERE tenant_id = ?
                GROUP BY status ORDER BY value DESC
                """, tenantId));

        List<ReportChartPoint> shippedByWeek = chartPoints(dsl.fetch("""
                SELECT TO_CHAR(date_trunc('week', created_at), 'Mon DD') AS label,
                       COALESCE(SUM(ABS(quantity_delta)), 0) AS value
                FROM inventory_ledger
                WHERE tenant_id = ?
                  AND movement_type = 'SHIP'
                  AND created_at >= NOW() - (? || ' days')::interval
                GROUP BY date_trunc('week', created_at)
                ORDER BY date_trunc('week', created_at)
                """, tenantId, periodDays));

        return new FulfillmentSummaryReport(
                unitsShipped30d,
                openOrderLines,
                fillRatePercent,
                ordersByStatus,
                shippedByWeek
        );
    }

    public PurchaseSpendReport purchaseSpend(int periodDays) {
        UUID tenantId = TenantContext.requireTenantId();
        String currency = resolveCurrency(tenantId);

        BigDecimal totalSpend = dsl.fetchOne("""
                SELECT COALESCE(SUM(pol.qty_ordered * pol.unit_cost), 0)
                FROM purchase_order_lines pol
                JOIN purchase_orders po ON po.id = pol.purchase_order_id AND po.tenant_id = pol.tenant_id
                WHERE pol.tenant_id = ?
                  AND po.created_at >= NOW() - (? || ' days')::interval
                """, tenantId, periodDays).into(BigDecimal.class);

        List<ReportChartPoint> spendBySupplier = chartPoints(dsl.fetch("""
                SELECT s.name AS label, COALESCE(SUM(pol.qty_ordered * pol.unit_cost), 0) AS value
                FROM purchase_order_lines pol
                JOIN purchase_orders po ON po.id = pol.purchase_order_id AND po.tenant_id = pol.tenant_id
                JOIN suppliers s ON s.id = po.supplier_id AND s.tenant_id = po.tenant_id
                WHERE pol.tenant_id = ?
                  AND po.created_at >= NOW() - (? || ' days')::interval
                GROUP BY s.name ORDER BY value DESC
                """, tenantId, periodDays));

        List<ReportChartPoint> spendByMonth = chartPoints(dsl.fetch("""
                SELECT TO_CHAR(po.created_at, 'YYYY-MM') AS label,
                       COALESCE(SUM(pol.qty_ordered * pol.unit_cost), 0) AS value
                FROM purchase_order_lines pol
                JOIN purchase_orders po ON po.id = pol.purchase_order_id AND po.tenant_id = pol.tenant_id
                WHERE pol.tenant_id = ?
                  AND po.created_at >= NOW() - (? || ' days')::interval
                GROUP BY 1 ORDER BY 1
                """, tenantId, periodDays));

        Result<Record> rows = dsl.fetch("""
                SELECT po.id, po.number, s.name AS supplier_name, po.status,
                       COALESCE(SUM(pol.qty_ordered * pol.unit_cost), 0) AS total_spend
                FROM purchase_orders po
                JOIN suppliers s ON s.id = po.supplier_id AND s.tenant_id = po.tenant_id
                JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id AND pol.tenant_id = po.tenant_id
                WHERE po.tenant_id = ?
                  AND po.created_at >= NOW() - (? || ' days')::interval
                GROUP BY po.id, po.number, s.name, po.status
                ORDER BY total_spend DESC
                """, tenantId, periodDays);

        List<PurchaseSpendRow> spendRows = new ArrayList<>();
        for (Record row : rows) {
            spendRows.add(new PurchaseSpendRow(
                    row.get("id", UUID.class),
                    row.get("number", String.class),
                    row.get("supplier_name", String.class),
                    row.get("status", String.class),
                    row.get("total_spend", BigDecimal.class)
            ));
        }

        return new PurchaseSpendReport(
                totalSpend.setScale(2, RoundingMode.HALF_UP),
                currency,
                spendBySupplier,
                spendByMonth,
                spendRows
        );
    }

    public ReturnsAnalysisReport returnsAnalysis(int periodDays) {
        UUID tenantId = TenantContext.requireTenantId();

        BigDecimal totalReturns = dsl.fetchOne("""
                SELECT COUNT(*)::numeric FROM returns WHERE tenant_id = ?
                """, tenantId).into(BigDecimal.class);

        BigDecimal shippedOrders = dsl.fetchOne("""
                SELECT COUNT(*)::numeric FROM sales_orders
                WHERE tenant_id = ? AND status IN ('SHIPPED', 'PARTIALLY_SHIPPED', 'CLOSED')
                """, tenantId).into(BigDecimal.class);

        BigDecimal returnRatePercent = shippedOrders.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalReturns.multiply(BigDecimal.valueOf(100)).divide(shippedOrders, 2, RoundingMode.HALF_UP);

        List<ReportChartPoint> returnsByStatus = chartPoints(dsl.fetch("""
                SELECT status AS label, COUNT(*)::numeric AS value
                FROM returns WHERE tenant_id = ?
                GROUP BY status ORDER BY value DESC
                """, tenantId));

        List<ReportChartPoint> dispositionBreakdown = chartPoints(dsl.fetch("""
                SELECT COALESCE(rl.disposition, 'PENDING') AS label, COUNT(*)::numeric AS value
                FROM return_lines rl
                WHERE rl.tenant_id = ?
                GROUP BY rl.disposition ORDER BY value DESC
                """, tenantId));

        Result<Record> rows = dsl.fetch("""
                SELECT r.id, r.number, c.name AS customer_name, so.number AS sales_order_number,
                       r.status, COUNT(rl.id) AS line_count
                FROM returns r
                JOIN sales_orders so ON so.id = r.sales_order_id AND so.tenant_id = r.tenant_id
                JOIN customers c ON c.id = so.customer_id AND c.tenant_id = r.tenant_id
                LEFT JOIN return_lines rl ON rl.return_id = r.id AND rl.tenant_id = r.tenant_id
                WHERE r.tenant_id = ?
                  AND r.created_at >= NOW() - (? || ' days')::interval
                GROUP BY r.id, r.number, c.name, so.number, r.status
                ORDER BY r.created_at DESC
                """, tenantId, periodDays);

        List<ReturnsAnalysisRow> returnRows = new ArrayList<>();
        for (Record row : rows) {
            returnRows.add(new ReturnsAnalysisRow(
                    row.get("id", UUID.class),
                    row.get("number", String.class),
                    row.get("customer_name", String.class),
                    row.get("sales_order_number", String.class),
                    row.get("status", String.class),
                    row.get("line_count", Long.class)
            ));
        }

        return new ReturnsAnalysisReport(
                totalReturns,
                returnRatePercent,
                returnsByStatus,
                dispositionBreakdown,
                returnRows
        );
    }

    private List<ReportChartPoint> chartPoints(Result<Record> rows) {
        List<ReportChartPoint> points = new ArrayList<>();
        for (Record row : rows) {
            points.add(new ReportChartPoint(
                    row.get("label", String.class),
                    row.get("value", BigDecimal.class)
            ));
        }
        return points;
    }

    private String resolveCurrency(UUID tenantId) {
        return settingsRepository.findByTenantId(tenantId)
                .map(s -> s.getSettings().get("currency"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse("USD");
    }
}

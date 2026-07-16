import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download, FileBarChart } from 'lucide-react';
import { apiClient } from '@/api/client';
import type {
  CogsLedgerReport,
  DemandChartPoint,
  FulfillmentSummaryReport,
  InventoryValuationReport,
  ProfitMarginReport,
  PurchaseSpendReport,
  ReportChartPoint,
  ReturnsAnalysisReport,
  SalesPerformanceReport,
  StockTurnoverReport,
  TenantSettingsMap,
} from '@/api/types';
import {
  formatCurrency,
  formatNumber,
  ReportBarChart,
  ReportDataTable,
  ReportLineChart,
  ReportPieChart,
  StatCard,
} from '@/components/reports/ReportCharts';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/Skeleton';

type ReportTab =
  | 'valuation'
  | 'timeTravel'
  | 'turnover'
  | 'cogs'
  | 'profit'
  | 'sales'
  | 'fulfillment'
  | 'purchases'
  | 'returns'
  | 'demand';

const TABS: { id: ReportTab; label: string }[] = [
  { id: 'valuation', label: 'Inventory valuation' },
  { id: 'timeTravel', label: 'Time-travel valuation' },
  { id: 'turnover', label: 'Stock turnover' },
  { id: 'cogs', label: 'COGS ledger' },
  { id: 'profit', label: 'Profit & margin' },
  { id: 'sales', label: 'Sales performance' },
  { id: 'fulfillment', label: 'Fulfillment' },
  { id: 'purchases', label: 'Purchase spend' },
  { id: 'returns', label: 'Returns' },
  { id: 'demand', label: 'Demand sensing' },
];

type AsOfValuationResponse = {
  asOfDate: string;
  totalValue: number;
  currency: string;
  lines: Array<{
    variantId: string;
    locationId: string;
    quantityOnHand: number;
    totalValue: number;
  }>;
};

type ValuationHistoryResponse = {
  currency: string;
  points: Array<{ asOfDate: string; totalValue: number }>;
};

function downloadCsv(filename: string, headers: string[], rows: string[][]) {
  const lines = [headers.join(','), ...rows.map((r) => r.map((c) => `"${c}"`).join(','))];
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function groupByWarehouse(rows: InventoryValuationReport['rows']): ReportChartPoint[] {
  const totals = new Map<string, number>();
  for (const row of rows) {
    totals.set(row.warehouseName, (totals.get(row.warehouseName) ?? 0) + row.totalValue);
  }
  return Array.from(totals.entries()).map(([label, value]) => ({ label, value }));
}

export function ReportsPage() {
  const [tab, setTab] = useState<ReportTab>('profit');
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10));

  const { data: settings } = useQuery({
    queryKey: ['settings'],
    queryFn: async () => (await apiClient.get<TenantSettingsMap>('/api/v1/settings')).data,
    retry: false,
  });

  const timezone =
    typeof settings?.timezone === 'string' ? settings.timezone : 'America/New_York';

  const valuation = useQuery({
    queryKey: ['reports', 'valuation'],
    queryFn: async () =>
      (await apiClient.get<InventoryValuationReport>('/api/v1/reports/inventory-valuation')).data,
    enabled: tab === 'valuation',
    retry: false,
  });

  const asOfIso = useMemo(() => {
    try {
      return new Date(`${asOfDate}T23:59:59.999Z`).toISOString();
    } catch {
      return new Date().toISOString();
    }
  }, [asOfDate]);

  const timeTravel = useQuery({
    queryKey: ['reports', 'valuation-asof', asOfIso],
    queryFn: async () =>
      (
        await apiClient.get<AsOfValuationResponse>('/api/v1/reports/valuation', {
          params: { asOfDate: asOfIso },
        })
      ).data,
    enabled: tab === 'timeTravel',
    retry: false,
  });

  const timeTravelHistory = useQuery({
    queryKey: ['reports', 'valuation-history'],
    queryFn: async () =>
      (
        await apiClient.get<ValuationHistoryResponse>('/api/v1/reports/valuation/history', {
          params: { days: 90, points: 30 },
        })
      ).data,
    enabled: tab === 'timeTravel',
    retry: false,
  });

  const turnover = useQuery({
    queryKey: ['reports', 'turnover'],
    queryFn: async () =>
      (await apiClient.get<StockTurnoverReport>('/api/v1/reports/stock-turnover?periodDays=90'))
        .data,
    enabled: tab === 'turnover',
    retry: false,
  });

  const cogs = useQuery({
    queryKey: ['reports', 'cogs'],
    queryFn: async () =>
      (await apiClient.get<CogsLedgerReport>('/api/v1/reports/cogs-ledger')).data,
    enabled: tab === 'cogs',
    retry: false,
  });

  const profit = useQuery({
    queryKey: ['reports', 'profit'],
    queryFn: async () =>
      (await apiClient.get<ProfitMarginReport>('/api/v1/reports/profit-margin?periodDays=90')).data,
    enabled: tab === 'profit',
    retry: false,
  });

  const sales = useQuery({
    queryKey: ['reports', 'sales'],
    queryFn: async () =>
      (await apiClient.get<SalesPerformanceReport>('/api/v1/reports/sales-performance?periodDays=90'))
        .data,
    enabled: tab === 'sales',
    retry: false,
  });

  const fulfillment = useQuery({
    queryKey: ['reports', 'fulfillment'],
    queryFn: async () =>
      (await apiClient.get<FulfillmentSummaryReport>('/api/v1/reports/fulfillment-summary?periodDays=30'))
        .data,
    enabled: tab === 'fulfillment',
    retry: false,
  });

  const purchases = useQuery({
    queryKey: ['reports', 'purchases'],
    queryFn: async () =>
      (await apiClient.get<PurchaseSpendReport>('/api/v1/reports/purchase-spend?periodDays=90')).data,
    enabled: tab === 'purchases',
    retry: false,
  });

  const returns = useQuery({
    queryKey: ['reports', 'returns'],
    queryFn: async () =>
      (await apiClient.get<ReturnsAnalysisReport>('/api/v1/reports/returns-analysis?periodDays=90'))
        .data,
    enabled: tab === 'returns',
    retry: false,
  });

  const demandChart = useQuery({
    queryKey: ['forecasting', 'chart-data'],
    queryFn: async () =>
      (await apiClient.get<DemandChartPoint[]>('/api/v1/forecasting/chart-data')).data,
    enabled: tab === 'demand',
    retry: false,
  });

  const active =
    tab === 'valuation'
      ? valuation
      : tab === 'timeTravel'
        ? timeTravel
        : tab === 'turnover'
          ? turnover
          : tab === 'cogs'
            ? cogs
            : tab === 'profit'
              ? profit
              : tab === 'sales'
                ? sales
                : tab === 'fulfillment'
                  ? fulfillment
                  : tab === 'purchases'
                    ? purchases
                    : tab === 'demand'
                      ? demandChart
                      : returns;

  const historyChart = useMemo(
    () =>
      (timeTravelHistory.data?.points ?? []).map((p) => ({
        label: new Date(p.asOfDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        value: Number(p.totalValue),
      })),
    [timeTravelHistory.data],
  );

  const warehouseChart = useMemo(
    () => (valuation.data ? groupByWarehouse(valuation.data.rows) : []),
    [valuation.data]
  );

  const exportCsv = () => {
    const stamp = new Date().toLocaleString('en-US', { timeZone: timezone });
    if (tab === 'valuation' && valuation.data) {
      downloadCsv(
        `inventory-valuation-${stamp}.csv`,
        ['Warehouse', 'SKU', 'Product', 'On hand', 'Avg cost', 'Value'],
        valuation.data.rows.map((r) => [
          r.warehouseName,
          r.sku,
          r.productName,
          String(r.onHand),
          String(r.avgCost),
          String(r.totalValue),
        ])
      );
    } else if (tab === 'turnover' && turnover.data) {
      downloadCsv(
        `stock-turnover-${stamp}.csv`,
        ['SKU', 'Product', 'Shipped', 'Avg on hand', 'Turnover'],
        turnover.data.rows.map((r) => [
          r.sku,
          r.productName,
          String(r.unitsShipped),
          String(r.averageOnHand),
          String(r.turnoverRate),
        ])
      );
    } else if (tab === 'cogs' && cogs.data) {
      downloadCsv(
        `cogs-ledger-${stamp}.csv`,
        ['Channel', 'Customer', 'Movement', 'Qty', 'Unit cost', 'COGS'],
        cogs.data.rows.map((r) => [
          r.channel,
          r.customerName ?? '—',
          r.movementType,
          String(r.quantity),
          String(r.unitCost),
          String(r.cogsAmount),
        ])
      );
    } else if (tab === 'profit' && profit.data) {
      downloadCsv(
        `profit-margin-${stamp}.csv`,
        ['SKU', 'Product', 'Revenue', 'COGS', 'Gross profit', 'Margin %'],
        profit.data.byProduct.map((r) => [
          r.sku,
          r.productName,
          String(r.revenue),
          String(r.cogs),
          String(r.grossProfit),
          String(r.marginPercent),
        ])
      );
    }
  };

  return (
    <div className="p-6">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <FileBarChart className="h-8 w-8 text-accent" />
          <div>
            <h1 className="text-2xl font-bold text-text">Reports</h1>
            <p className="text-sm text-text-muted">
              WMS analytics and financial insights · {timezone}
            </p>
          </div>
        </div>
        <Button variant="secondary" onClick={exportCsv} disabled={!active.data}>
          <Download className="mr-2 h-4 w-4" />
          Export CSV
        </Button>
      </div>

      <div className="mb-6 flex flex-wrap gap-2">
        {TABS.map(({ id, label }) => (
          <Button key={id} variant={tab === id ? 'primary' : 'secondary'} onClick={() => setTab(id)}>
            {label}
          </Button>
        ))}
      </div>

      {active.isLoading && <TableSkeleton rows={10} cols={6} />}

      {tab === 'valuation' && valuation.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <StatCard
              label="Total inventory value"
              value={formatCurrency(valuation.data.grandTotal, valuation.data.currency)}
            />
            <StatCard label="SKUs tracked" value={String(valuation.data.rows.length)} />
            <StatCard
              label="Warehouses"
              value={String(warehouseChart.length)}
              hint="Valuation by location"
            />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportPieChart title="Value by warehouse" data={warehouseChart} />
            <ReportBarChart
              title="Top SKUs by value"
              data={valuation.data.rows
                .slice()
                .sort((a, b) => b.totalValue - a.totalValue)
                .slice(0, 8)
                .map((row) => ({ label: row.sku, value: row.totalValue }))}
              valueFormatter={(v) => formatCurrency(v, valuation.data!.currency)}
              layout="vertical"
            />
          </div>
          <Card>
            <CardHeader
              title="Inventory valuation detail"
              description="On-hand quantity × average cost by warehouse"
            />
            <ReportDataTable
              headers={['Warehouse', 'SKU', 'Product', 'On hand', 'Avg cost', 'Value']}
              rows={valuation.data.rows.map((r) => [
                r.warehouseName,
                r.sku,
                r.productName,
                formatNumber(r.onHand),
                formatCurrency(r.avgCost, valuation.data!.currency),
                formatCurrency(r.totalValue, valuation.data!.currency),
              ])}
            />
          </Card>
        </div>
      )}

      {tab === 'timeTravel' && (
        <div className="space-y-6" data-testid="time-travel-valuation">
          <div className="flex flex-wrap items-end gap-3">
            <label className="block text-sm">
              <span className="mb-1 block text-text-muted">As-of date</span>
              <input
                type="date"
                className="h-10 rounded-md border border-border bg-surface-raised px-3 text-sm"
                value={asOfDate}
                onChange={(e) => setAsOfDate(e.target.value)}
                data-testid="as-of-date"
              />
            </label>
          </div>
          {timeTravel.data && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <StatCard
                label="Retroactive asset value"
                value={formatCurrency(Number(timeTravel.data.totalValue), timeTravel.data.currency)}
              />
              <StatCard label="Ledger buckets" value={String(timeTravel.data.lines.length)} />
              <StatCard
                label="As of"
                value={new Date(timeTravel.data.asOfDate).toLocaleDateString()}
              />
            </div>
          )}
          {historyChart.length > 0 && (
            <ReportLineChart
              title="Historical asset value (90d)"
              data={historyChart}
              valueFormatter={(v) =>
                formatCurrency(v, timeTravelHistory.data?.currency ?? timeTravel.data?.currency ?? 'USD')
              }
            />
          )}
          {timeTravel.data && (
            <Card>
              <CardHeader
                title="As-of ledger valuation"
                description="SUM(quantity_delta) and SUM(quantity_delta × unit_cost) by variant/location"
              />
              <ReportDataTable
                headers={['Variant', 'Location', 'Qty', 'Value']}
                rows={timeTravel.data.lines.map((r) => [
                  r.variantId.slice(0, 8),
                  r.locationId.slice(0, 8),
                  formatNumber(Number(r.quantityOnHand)),
                  formatCurrency(Number(r.totalValue), timeTravel.data!.currency),
                ])}
              />
            </Card>
          )}
        </div>
      )}

      {tab === 'turnover' && turnover.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard label="Products tracked" value={String(turnover.data.rows.length)} />
            <StatCard
              label="Avg turnover"
              value={formatNumber(
                turnover.data.rows.reduce((sum, row) => sum + row.turnoverRate, 0) /
                  Math.max(turnover.data.rows.length, 1)
              )}
              hint="90-day period"
            />
            <StatCard
              label="Units shipped"
              value={formatNumber(
                turnover.data.rows.reduce((sum, row) => sum + row.unitsShipped, 0)
              )}
            />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportBarChart
              title="Turnover rate by SKU"
              data={turnover.data.rows
                .slice()
                .sort((a, b) => b.turnoverRate - a.turnoverRate)
                .slice(0, 10)
                .map((row) => ({ label: row.sku, value: row.turnoverRate }))}
              layout="vertical"
            />
            <ReportBarChart
              title="Units shipped (90 days)"
              data={turnover.data.rows
                .slice()
                .sort((a, b) => b.unitsShipped - a.unitsShipped)
                .slice(0, 10)
                .map((row) => ({ label: row.sku, value: row.unitsShipped }))}
            />
          </div>
          <ReportDataTable
            headers={['SKU', 'Product', 'Shipped', 'Avg on hand', 'Turnover']}
            rows={turnover.data.rows.map((r) => [
              r.sku,
              r.productName,
              formatNumber(r.unitsShipped),
              formatNumber(r.averageOnHand),
              formatNumber(r.turnoverRate),
            ])}
          />
        </div>
      )}

      {tab === 'cogs' && cogs.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <StatCard
              label="Total COGS"
              value={formatCurrency(cogs.data.totalCogs, cogs.data.currency)}
            />
            <StatCard label="Ledger entries" value={String(cogs.data.rows.length)} />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportPieChart
              title="COGS by channel"
              data={Object.entries(
                cogs.data.rows.reduce<Record<string, number>>((acc, row) => {
                  acc[row.channel] = (acc[row.channel] ?? 0) + row.cogsAmount;
                  return acc;
                }, {})
              ).map(([label, value]) => ({ label, value }))}
            />
            <ReportBarChart
              title="COGS by movement type"
              data={Object.entries(
                cogs.data.rows.reduce<Record<string, number>>((acc, row) => {
                  acc[row.movementType] = (acc[row.movementType] ?? 0) + row.cogsAmount;
                  return acc;
                }, {})
              ).map(([label, value]) => ({ label, value }))}
              valueFormatter={(v) => formatCurrency(v, cogs.data!.currency)}
            />
          </div>
          <ReportDataTable
            headers={['Channel', 'Customer', 'Movement', 'Qty', 'Unit cost', 'COGS']}
            rows={cogs.data.rows.map((r) => [
              r.channel,
              r.customerName ?? '—',
              r.movementType,
              formatNumber(r.quantity),
              formatCurrency(r.unitCost, cogs.data!.currency),
              formatCurrency(r.cogsAmount, cogs.data!.currency),
            ])}
          />
        </div>
      )}

      {tab === 'profit' && profit.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Revenue"
              value={formatCurrency(profit.data.totalRevenue, profit.data.currency)}
            />
            <StatCard
              label="COGS"
              value={formatCurrency(profit.data.totalCogs, profit.data.currency)}
            />
            <StatCard
              label="Gross profit"
              value={formatCurrency(profit.data.grossProfit, profit.data.currency)}
            />
            <StatCard
              label="Gross margin"
              value={`${formatNumber(profit.data.grossMarginPercent)}%`}
            />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportLineChart
              title="Revenue trend"
              data={profit.data.revenueByMonth}
              valueFormatter={(v) => formatCurrency(v, profit.data!.currency)}
            />
            <ReportLineChart
              title="Gross profit trend"
              data={profit.data.profitByMonth}
              valueFormatter={(v) => formatCurrency(v, profit.data!.currency)}
            />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportBarChart
              title="Profit by product"
              data={profit.data.byProduct.map((row) => ({
                label: row.sku,
                value: row.grossProfit,
              }))}
              valueFormatter={(v) => formatCurrency(v, profit.data!.currency)}
              layout="vertical"
            />
            <ReportBarChart
              title="Profit by customer"
              data={profit.data.byCustomer.map((row) => ({
                label: row.customerName,
                value: row.grossProfit,
              }))}
              valueFormatter={(v) => formatCurrency(v, profit.data!.currency)}
              layout="vertical"
            />
          </div>
          <ReportDataTable
            headers={['SKU', 'Product', 'Revenue', 'COGS', 'Gross profit', 'Margin %']}
            rows={profit.data.byProduct.map((r) => [
              r.sku,
              r.productName,
              formatCurrency(r.revenue, profit.data!.currency),
              formatCurrency(r.cogs, profit.data!.currency),
              formatCurrency(r.grossProfit, profit.data!.currency),
              `${formatNumber(r.marginPercent)}%`,
            ])}
          />
        </div>
      )}

      {tab === 'sales' && sales.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <StatCard
              label="Total revenue"
              value={formatCurrency(sales.data.totalRevenue, sales.data.currency)}
            />
            <StatCard label="Orders" value={formatNumber(sales.data.totalOrders)} />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportLineChart
              title="Revenue by month"
              data={sales.data.revenueByMonth}
              valueFormatter={(v) => formatCurrency(v, sales.data!.currency)}
            />
            <ReportPieChart title="Orders by status" data={sales.data.ordersByStatus} />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportBarChart
              title="Revenue by channel"
              data={sales.data.revenueByChannel}
              valueFormatter={(v) => formatCurrency(v, sales.data!.currency)}
            />
            <ReportBarChart
              title="Revenue by customer"
              data={sales.data.revenueByCustomer}
              valueFormatter={(v) => formatCurrency(v, sales.data!.currency)}
              layout="vertical"
            />
          </div>
        </div>
      )}

      {tab === 'fulfillment' && fulfillment.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard
              label="Units shipped (30d)"
              value={formatNumber(fulfillment.data.unitsShipped30d)}
            />
            <StatCard
              label="Open order lines"
              value={formatNumber(fulfillment.data.openOrderLines)}
            />
            <StatCard
              label="Fill rate"
              value={`${formatNumber(fulfillment.data.fillRatePercent)}%`}
            />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportPieChart title="Orders by status" data={fulfillment.data.ordersByStatus} />
            <ReportBarChart title="Units shipped by week" data={fulfillment.data.shippedByWeek} />
          </div>
        </div>
      )}

      {tab === 'purchases' && purchases.data && (
        <div className="space-y-6">
          <StatCard
            label="Total purchase spend"
            value={formatCurrency(purchases.data.totalSpend, purchases.data.currency)}
          />
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportBarChart
              title="Spend by supplier"
              data={purchases.data.spendBySupplier}
              valueFormatter={(v) => formatCurrency(v, purchases.data!.currency)}
              layout="vertical"
            />
            <ReportLineChart
              title="Spend by month"
              data={purchases.data.spendByMonth}
              valueFormatter={(v) => formatCurrency(v, purchases.data!.currency)}
            />
          </div>
          <ReportDataTable
            headers={['PO number', 'Supplier', 'Status', 'Total spend']}
            rows={purchases.data.rows.map((r) => [
              r.number,
              r.supplierName,
              r.status,
              formatCurrency(r.totalSpend, purchases.data!.currency),
            ])}
          />
        </div>
      )}

      {tab === 'returns' && returns.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <StatCard label="Total returns" value={formatNumber(returns.data.totalReturns)} />
            <StatCard
              label="Return rate"
              value={`${formatNumber(returns.data.returnRatePercent)}%`}
              hint="Returns vs shipped orders"
            />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <ReportPieChart title="Returns by status" data={returns.data.returnsByStatus} />
            <ReportPieChart title="Disposition breakdown" data={returns.data.dispositionBreakdown} />
          </div>
          <ReportDataTable
            headers={['RMA', 'Customer', 'Sales order', 'Status', 'Lines']}
            rows={returns.data.rows.map((r) => [
              r.number,
              r.customerName,
              r.salesOrderNumber,
              r.status,
              String(r.lineCount),
            ])}
          />
        </div>
      )}

      {tab === 'demand' && demandChart.data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard
              label="SKUs forecasted"
              value={formatNumber(demandChart.data.length)}
              hint="Active demand sensing windows"
            />
            <StatCard
              label="Avg confidence"
              value={`${formatNumber(
                demandChart.data.reduce((s, p) => s + p.confidenceScore, 0) /
                  Math.max(demandChart.data.length, 1)
              )}%`}
            />
            <StatCard
              label="Seasonal index"
              value={formatNumber(
                demandChart.data.reduce((s, p) => s + p.seasonalityIndex, 0) /
                  Math.max(demandChart.data.length, 1)
              )}
            />
          </div>
          <ReportBarChart
            title="30-day velocity by SKU"
            data={demandChart.data.slice(0, 12).map((p) => ({
              label: p.sku,
              value: Number(p.historicalVelocity),
            }))}
          />
          <ReportBarChart
            title="Recommended replenishment qty"
            data={demandChart.data.slice(0, 12).map((p) => ({
              label: p.sku,
              value: Number(p.forecastQty),
            }))}
          />
          <ReportDataTable
            headers={['SKU', 'Velocity', 'Forecast qty', 'Seasonality', 'Confidence']}
            rows={demandChart.data.map((p) => [
              p.sku,
              formatNumber(p.historicalVelocity),
              formatNumber(p.forecastQty),
              formatNumber(p.seasonalityIndex),
              `${formatNumber(p.confidenceScore)}%`,
            ])}
          />
        </div>
      )}
    </div>
  );
}

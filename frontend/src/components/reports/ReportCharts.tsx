import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useMemo } from 'react';
import type { ReportChartPoint } from '@/api/types';
import { Card } from '@/components/ui/Card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useClientSort } from '@/hooks/useClientSort';

const CHART_COLORS = ['#55ACEE', '#2dd4bf', '#f59e0b', '#a78bfa', '#f472b6', '#34d399', '#fb7185'];

export function formatCurrency(value: number, currency = 'USD') {
  return value.toLocaleString(undefined, { style: 'currency', currency, maximumFractionDigits: 0 });
}

export function formatNumber(value: number) {
  return value.toLocaleString(undefined, { maximumFractionDigits: 1 });
}

function toChartData(points: ReportChartPoint[]) {
  return points.map((point) => ({ name: point.label, value: Number(point.value) }));
}

export function StatCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <Card className="p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{label}</p>
      <p className="mt-2 text-2xl font-bold text-text">{value}</p>
      {hint && <p className="mt-1 text-xs text-text-muted">{hint}</p>}
    </Card>
  );
}

export function ReportBarChart({
  title,
  data,
  valueFormatter = formatNumber,
  layout = 'horizontal',
}: {
  title: string;
  data: ReportChartPoint[];
  valueFormatter?: (value: number) => string;
  layout?: 'horizontal' | 'vertical';
}) {
  const chartData = toChartData(data);
  if (chartData.length === 0) {
    return (
      <Card className="p-4">
        <p className="mb-2 text-sm font-semibold text-text">{title}</p>
        <p className="text-sm text-text-muted">No data for this period.</p>
      </Card>
    );
  }

  return (
    <Card className="p-4">
      <p className="mb-4 text-sm font-semibold text-text">{title}</p>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          {layout === 'vertical' ? (
            <BarChart data={chartData} margin={{ top: 8, right: 8, left: 8, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="name" tick={{ fontSize: 12 }} />
              <YAxis tickFormatter={(v) => valueFormatter(Number(v))} tick={{ fontSize: 12 }} />
              <Tooltip formatter={(value) => valueFormatter(Number(value))} />
              <Bar dataKey="value" fill="#55ACEE" radius={[4, 4, 0, 0]} />
            </BarChart>
          ) : (
            <BarChart data={chartData} layout="vertical" margin={{ top: 8, right: 16, left: 16, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis type="number" tickFormatter={(v) => valueFormatter(Number(v))} tick={{ fontSize: 12 }} />
              <YAxis type="category" dataKey="name" width={100} tick={{ fontSize: 12 }} />
              <Tooltip formatter={(value) => valueFormatter(Number(value))} />
              <Bar dataKey="value" fill="#55ACEE" radius={[0, 4, 4, 0]} />
            </BarChart>
          )}
        </ResponsiveContainer>
      </div>
    </Card>
  );
}

export function ReportLineChart({
  title,
  data,
  valueFormatter = formatNumber,
}: {
  title: string;
  data: ReportChartPoint[];
  valueFormatter?: (value: number) => string;
}) {
  const chartData = toChartData(data);
  if (chartData.length === 0) {
    return (
      <Card className="p-4">
        <p className="mb-2 text-sm font-semibold text-text">{title}</p>
        <p className="text-sm text-text-muted">No data for this period.</p>
      </Card>
    );
  }

  return (
    <Card className="p-4">
      <p className="mb-4 text-sm font-semibold text-text">{title}</p>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis dataKey="name" tick={{ fontSize: 12 }} />
            <YAxis tickFormatter={(v) => valueFormatter(Number(v))} tick={{ fontSize: 12 }} />
            <Tooltip formatter={(value) => valueFormatter(Number(value))} />
            <Legend />
            <Line type="monotone" dataKey="value" stroke="#55ACEE" strokeWidth={2} dot={{ r: 3 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </Card>
  );
}

export function ReportPieChart({
  title,
  data,
}: {
  title: string;
  data: ReportChartPoint[];
}) {
  const chartData = toChartData(data);
  if (chartData.length === 0) {
    return (
      <Card className="p-4">
        <p className="mb-2 text-sm font-semibold text-text">{title}</p>
        <p className="text-sm text-text-muted">No data for this period.</p>
      </Card>
    );
  }

  return (
    <Card className="p-4">
      <p className="mb-4 text-sm font-semibold text-text">{title}</p>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={chartData}
              dataKey="value"
              nameKey="name"
              cx="50%"
              cy="50%"
              outerRadius={90}
              label={({ name, percent }) =>
                `${name} ${((percent ?? 0) * 100).toFixed(0)}%`
              }
            >
              {chartData.map((_, index) => (
                <Cell key={index} fill={CHART_COLORS[index % CHART_COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </Card>
  );
}

export function ReportDataTable({
  headers,
  rows,
}: {
  headers: string[];
  rows: (string | number)[][];
}) {
  const accessors = useMemo(() => {
    const map: Record<string, (row: (string | number)[]) => string | number> = {};
    headers.forEach((_, index) => {
      map[`c${index}`] = (row) => row[index] ?? '';
    });
    return map;
  }, [headers]);

  const { sort, toggle, sorted } = useClientSort(rows, accessors, {
    key: 'c0',
    dir: 'asc',
  });

  return (
    <Card padding="none" className="overflow-hidden">
      <Table className="min-w-[720px]">
        <TableHeader>
          <TableRow>
            {headers.map((header, index) => (
              <TableHead
                key={header}
                sortable
                sortKey={`c${index}`}
                sort={sort}
                onSort={toggle}
              >
                {header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.length === 0 ? (
            <TableRow>
              <TableCell colSpan={headers.length} className="py-8 text-center text-text-muted" align="center">
                No rows to display
              </TableCell>
            </TableRow>
          ) : (
            sorted.map((row, rowIndex) => (
              <TableRow key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <TableCell key={cellIndex}>{cell}</TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </Card>
  );
}

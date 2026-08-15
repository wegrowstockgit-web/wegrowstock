import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { MODULE_LABELS, TIER_LABELS } from '@invsys/shared-types';
import { fetchCommercialReport } from './api';

export function AdminCommercialReports() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'reports', 'commercial'],
    queryFn: fetchCommercialReport,
  });

  if (isLoading) {
    return <p className="text-sm text-text-muted">Loading commercial reports…</p>;
  }

  if (isError || !data) {
    return <p className="text-sm text-danger">Failed to load commercial reports.</p>;
  }

  const tierData = data.tierDistribution.map((row) => ({
    name: TIER_LABELS[row.tier],
    count: row.count,
  }));

  const moduleData = data.moduleAdoption.map((row) => ({
    name: MODULE_LABELS[row.module],
    tenants: row.tenantCount,
  }));

  return (
    <div className="space-y-8" data-testid="commercial-reports">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Commercial reports</h2>
        <p className="mt-1 text-sm text-text-muted">
          Tier distribution, module adoption, and platform GMV trends.
        </p>
      </div>

      <section className="rounded-lg border border-border bg-surface-raised p-4">
        <h3 className="mb-4 text-sm font-semibold text-text">Tier distribution</h3>
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={tierData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
              <XAxis dataKey="name" stroke="var(--color-text-muted)" fontSize={12} />
              <YAxis stroke="var(--color-text-muted)" fontSize={12} allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  background: 'var(--color-surface-raised)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                }}
              />
              <Bar dataKey="count" fill="var(--color-accent)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="rounded-lg border border-border bg-surface-raised p-4">
        <h3 className="mb-4 text-sm font-semibold text-text">Module adoption</h3>
        <div className="h-72">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={moduleData} layout="vertical" margin={{ left: 24 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
              <XAxis type="number" stroke="var(--color-text-muted)" fontSize={12} allowDecimals={false} />
              <YAxis
                type="category"
                dataKey="name"
                width={160}
                stroke="var(--color-text-muted)"
                fontSize={11}
              />
              <Tooltip
                contentStyle={{
                  background: 'var(--color-surface-raised)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                }}
              />
              <Bar dataKey="tenants" fill="var(--color-accent-hover)" radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="rounded-lg border border-border bg-surface-raised p-4">
        <h3 className="mb-4 text-sm font-semibold text-text">GMV by month</h3>
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data.gmvByMonth}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
              <XAxis dataKey="month" stroke="var(--color-text-muted)" fontSize={12} />
              <YAxis stroke="var(--color-text-muted)" fontSize={12} />
              <Tooltip
                contentStyle={{
                  background: 'var(--color-surface-raised)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                }}
                formatter={(value) => [
                  `$${Number(value ?? 0).toLocaleString()}`,
                  'GMV',
                ]}
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="gmv"
                name="GMV"
                stroke="var(--color-accent)"
                strokeWidth={2}
                dot={{ fill: 'var(--color-accent)' }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>
    </div>
  );
}

import { useQuery } from '@tanstack/react-query';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@invsys/shared-ui';
import { PageHeader } from '@/features/layout/PageHeader';
import { fetchHealthReport } from './api';

export function AdminHealthReports() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'reports', 'health'],
    queryFn: fetchHealthReport,
  });

  if (isLoading) {
    return <p className="text-sm text-text-muted">Loading health reports…</p>;
  }

  if (isError || !data) {
    return <p className="text-sm text-danger">Failed to load health reports.</p>;
  }

  return (
    <div className="space-y-8" data-testid="health-reports">
      <PageHeader
        title="Health reports"
        description="Webhook failures, rate-limit pressure, and ledger growth across tenants."
      />

      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-text">Webhook failures (24h)</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tenant</TableHead>
              <TableHead>Endpoint</TableHead>
              <TableHead>Failures</TableHead>
              <TableHead>Last error</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.webhookFailures.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} className="text-text-muted">
                  No webhook failures in the last 24 hours.
                </TableCell>
              </TableRow>
            ) : (
              data.webhookFailures.map((row) => (
                <TableRow key={`${row.tenantSlug}-${row.endpoint}`}>
                  <TableCell className="font-medium">{row.tenantSlug}</TableCell>
                  <TableCell className="text-text-muted">{row.endpoint}</TableCell>
                  <TableCell>{row.failures24h}</TableCell>
                  <TableCell className="max-w-xs truncate text-text-muted">{row.lastError}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-text">Rate limit hits (24h)</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tenant</TableHead>
              <TableHead>Route</TableHead>
              <TableHead>Hits</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.rateLimitHits.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} className="text-text-muted">
                  No rate-limit events in the last 24 hours.
                </TableCell>
              </TableRow>
            ) : (
              data.rateLimitHits.map((row) => (
                <TableRow key={`${row.tenantSlug}-${row.route}`}>
                  <TableCell className="font-medium">{row.tenantSlug}</TableCell>
                  <TableCell className="text-text-muted">{row.route}</TableCell>
                  <TableCell>{row.hits24h}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-text">Ledger growth (30d)</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tenant</TableHead>
              <TableHead>New entries (30d)</TableHead>
              <TableHead>Total entries</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.ledgerGrowth.map((row) => (
              <TableRow key={row.tenantSlug}>
                <TableCell className="font-medium">{row.tenantSlug}</TableCell>
                <TableCell>{row.entries30d.toLocaleString()}</TableCell>
                <TableCell className="text-text-muted">
                  {row.totalEntries.toLocaleString()}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </section>
    </div>
  );
}

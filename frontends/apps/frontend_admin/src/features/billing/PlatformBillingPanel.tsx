import { useQuery } from '@tanstack/react-query';
import {
  PageSkeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@invsys/shared-ui';
import { fetchBillingOverview } from './api';

export function PlatformBillingPanel() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'billing', 'overview'],
    queryFn: fetchBillingOverview,
  });

  if (isLoading) {
    return <PageSkeleton label="Loading billing overview…" />;
  }

  if (isError || !data) {
    return <p className="text-sm text-danger">Failed to load platform billing.</p>;
  }

  return (
    <div className="space-y-6" data-testid="platform-billing">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Platform billing</h2>
        <p className="mt-1 text-sm text-text-muted">
          Estimated MRR from commercial tiers and per-tenant usage against plan limits.
        </p>
      </div>

      <div className="rounded-lg border border-border bg-surface-raised p-5">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
          Estimated MRR
        </p>
        <p className="mt-1 text-3xl font-semibold tracking-tight text-text">
          ${Number(data.estimatedMrr).toLocaleString(undefined, { maximumFractionDigits: 0 })}
        </p>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tenant</TableHead>
            <TableHead>Tier</TableHead>
            <TableHead>Card</TableHead>
            <TableHead>Shipped lines</TableHead>
            <TableHead>Usage</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.tenants.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5} className="text-text-muted">
                No tenants with billing cards.
              </TableCell>
            </TableRow>
          ) : (
            data.tenants.map((row) => {
              const limit = row.usageLimits.shippedLinesPerMonth || 1;
              const pct = Math.min(100, Math.round((row.shippedLinesUsage / limit) * 100));
              return (
                <TableRow key={row.tenantId}>
                  <TableCell className="font-medium">{row.slug}</TableCell>
                  <TableCell className="text-text-muted">{row.tier}</TableCell>
                  <TableCell>{row.cardStatus}</TableCell>
                  <TableCell className="text-text-muted">
                    {row.shippedLinesUsage.toLocaleString()} / {limit.toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <div className="h-2 w-28 overflow-hidden rounded bg-surface">
                        <div
                          className="h-full rounded bg-accent"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                      <span className="text-xs text-text-muted">{pct}%</span>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })
          )}
        </TableBody>
      </Table>
    </div>
  );
}

import { useState } from 'react';
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
import { PageHeader } from '@/features/layout/PageHeader';
import { fetchAuditLogs, isImpersonationAudit } from './api';

export function PlatformAuditTrail() {
  const [impersonationOnly, setImpersonationOnly] = useState(false);
  const { data: rows = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'audit-logs', impersonationOnly],
    queryFn: () => fetchAuditLogs(100, impersonationOnly),
  });

  if (isLoading) {
    return <PageSkeleton label="Loading audit trail…" />;
  }

  if (isError) {
    return <p className="text-sm text-danger">Failed to load platform audit logs.</p>;
  }

  return (
    <div className="space-y-6" data-testid="platform-audit">
      <PageHeader
        title="Audit trail"
        description="Recent Super Admin mutations across the control plane."
        actions={
          <button
            type="button"
            className={
              impersonationOnly
                ? 'rounded border border-warning bg-warning/15 px-3 py-1.5 text-sm font-medium text-warning'
                : 'rounded border border-border px-3 py-1.5 text-sm font-medium hover:bg-surface'
            }
            onClick={() => setImpersonationOnly((value) => !value)}
            data-testid="audit-impersonation-filter"
          >
            🛡️ Impersonation Activity Only
          </button>
        }
      />

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>When</TableHead>
            <TableHead>Admin</TableHead>
            <TableHead>Action</TableHead>
            <TableHead>Tenant</TableHead>
            <TableHead>IP</TableHead>
            <TableHead>Diff</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6} className="text-text-muted">
                No audit events yet.
              </TableCell>
            </TableRow>
          ) : (
            rows.map((row) => {
              const impersonated = isImpersonationAudit(row);
              return (
                <TableRow
                  key={row.id}
                  className={impersonated ? 'bg-warning/10' : undefined}
                  data-testid={impersonated ? 'audit-impersonation-row' : undefined}
                >
                  <TableCell className="whitespace-nowrap text-text-muted">
                    {new Date(row.createdAt).toLocaleString()}
                  </TableCell>
                  <TableCell className="font-medium">{row.adminEmail}</TableCell>
                  <TableCell>
                    <span className="inline-flex flex-wrap items-center gap-2">
                      {row.action}
                      {impersonated ? (
                        <span
                          className="rounded bg-warning/20 px-1.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-warning"
                          data-testid="impersonation-badge"
                        >
                          Impersonation
                        </span>
                      ) : null}
                    </span>
                  </TableCell>
                  <TableCell className="font-mono text-xs text-text-muted">
                    {row.targetTenantId ?? '—'}
                  </TableCell>
                  <TableCell className="text-sm text-text-muted">{row.ipAddress ?? '—'}</TableCell>
                  <TableCell className="max-w-xs truncate text-sm text-text-muted">
                    {row.diffJson ?? '—'}
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

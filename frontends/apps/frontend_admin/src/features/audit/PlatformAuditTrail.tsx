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
import { fetchAuditLogs } from './api';

export function PlatformAuditTrail() {
  const { data: rows = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'audit-logs'],
    queryFn: () => fetchAuditLogs(100),
  });

  if (isLoading) {
    return <PageSkeleton label="Loading audit trail…" />;
  }

  if (isError) {
    return <p className="text-sm text-danger">Failed to load platform audit logs.</p>;
  }

  return (
    <div className="space-y-6" data-testid="platform-audit">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Audit trail</h2>
        <p className="mt-1 text-sm text-text-muted">
          Recent Super Admin mutations across the control plane.
        </p>
      </div>

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
            rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell className="whitespace-nowrap text-text-muted">
                  {new Date(row.createdAt).toLocaleString()}
                </TableCell>
                <TableCell className="font-medium">{row.adminEmail}</TableCell>
                <TableCell>{row.action}</TableCell>
                <TableCell className="font-mono text-xs text-text-muted">
                  {row.targetTenantId ?? '—'}
                </TableCell>
                <TableCell className="text-text-muted">{row.ipAddress ?? '—'}</TableCell>
                <TableCell className="max-w-xs truncate font-mono text-xs text-text-muted">
                  {row.diffJson ?? '—'}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}

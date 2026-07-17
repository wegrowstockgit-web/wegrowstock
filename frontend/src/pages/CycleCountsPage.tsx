import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardList } from 'lucide-react';
import { useState } from 'react';
import { apiClient } from '@/api/client';
import type { PendingVariance, PriorityAudit } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/Skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { CycleCountScanner } from '@/features/fulfillment/CycleCountScanner';
import { useClientSort } from '@/hooks/useClientSort';
import { useDashboardStream } from '@/hooks/useDashboardStream';
import { useSessionStore } from '@/stores/session';

function money(value: number | string): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value);
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
  }).format(n);
}

function qty(value: number | string): string {
  const n = Number(value);
  return Number.isFinite(n) ? String(n) : String(value);
}

function PriorityAuditsTable({
  audits,
  onOpen,
}: {
  audits: PriorityAudit[];
  onOpen: (id: string) => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    audits,
    {
      location: (a) => a.locationPath,
      reason: (a) => a.notes ?? '',
      created: (a) => a.createdAt,
    },
    { key: 'created', dir: 'desc' },
  );
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="location" sort={sort} onSort={toggle}>
            Location
          </TableHead>
          <TableHead sortable sortKey="reason" sort={sort} onSort={toggle}>
            Reason
          </TableHead>
          <TableHead sortable sortKey="created" sort={sort} onSort={toggle}>
            Created
          </TableHead>
          <TableHead>Action</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((audit) => (
          <TableRow key={audit.id} data-testid={`priority-audit-${audit.id}`}>
            <TableCell mono>{audit.locationPath}</TableCell>
            <TableCell>{audit.notes ?? 'Priority audit'}</TableCell>
            <TableCell>{new Date(audit.createdAt).toLocaleString()}</TableCell>
            <TableCell>
              <Button
                size="sm"
                onClick={() => onOpen(audit.id)}
                data-testid={`open-count-${audit.id}`}
              >
                Count bin
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function PendingVariancesTable({
  rows,
  onApprove,
  onRecount,
  busyId,
}: {
  rows: PendingVariance[];
  onApprove: (lineId: string) => void;
  onRecount: (lineId: string) => void;
  busyId: string | null;
}) {
  const { sort, toggle, sorted } = useClientSort(
    rows,
    {
      location: (r) => r.locationPath,
      sku: (r) => r.sku,
      delta: (r) => Number(r.financialDelta),
    },
    { key: 'delta', dir: 'desc' },
  );

  return (
    <div data-testid="pending-variances-table">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="location" sort={sort} onSort={toggle}>
            Location
          </TableHead>
          <TableHead sortable sortKey="sku" sort={sort} onSort={toggle}>
            SKU
          </TableHead>
          <TableHead>Expected Qty</TableHead>
          <TableHead>Counted Qty</TableHead>
          <TableHead sortable sortKey="delta" sort={sort} onSort={toggle}>
            Financial Delta
          </TableHead>
          <TableHead>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((row) => (
          <TableRow key={row.lineId} data-testid={`pending-variance-${row.lineId}`}>
            <TableCell mono>{row.locationPath}</TableCell>
            <TableCell mono>{row.sku}</TableCell>
            <TableCell mono>{qty(row.expectedQty)}</TableCell>
            <TableCell mono>{qty(row.countedQty)}</TableCell>
            <TableCell mono data-testid={`financial-delta-${row.lineId}`}>
              {money(row.financialDelta)}
            </TableCell>
            <TableCell>
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  loading={busyId === row.lineId}
                  onClick={() => onApprove(row.lineId)}
                  data-testid={`approve-variance-${row.lineId}`}
                >
                  Approve Ledger Adjustment
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  loading={busyId === row.lineId}
                  onClick={() => onRecount(row.lineId)}
                  data-testid={`request-recount-${row.lineId}`}
                >
                  Request Recount
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
    </div>
  );
}

export function CycleCountsPage() {
  const queryClient = useQueryClient();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canReview = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const [activeCountId, setActiveCountId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const { data: audits = [], isLoading } = useQuery({
    queryKey: ['cycle-counts', 'priority-audits'],
    queryFn: async () => {
      const res = await apiClient.get<PriorityAudit[]>('/api/v1/cycle-counts/priority-audits');
      return res.data;
    },
    retry: false,
  });

  const { data: pending = [], isLoading: pendingLoading } = useQuery({
    queryKey: ['cycle-counts', 'pending-variances'],
    queryFn: async () => {
      const res = await apiClient.get<PendingVariance[]>('/api/v1/cycle-counts/pending-variances');
      return res.data;
    },
    enabled: canReview,
    retry: false,
  });

  useDashboardStream(true);

  const approve = useMutation({
    mutationFn: async (lineId: string) => {
      setBusyId(lineId);
      await apiClient.post(`/api/v1/cycle-counts/lines/${lineId}/approve-adjustment`);
    },
    onSettled: async () => {
      setBusyId(null);
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts', 'pending-variances'] });
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts', 'priority-audits'] });
    },
  });

  const recount = useMutation({
    mutationFn: async (lineId: string) => {
      setBusyId(lineId);
      await apiClient.post(`/api/v1/cycle-counts/lines/${lineId}/request-recount`);
    },
    onSettled: async () => {
      setBusyId(null);
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts', 'pending-variances'] });
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts', 'priority-audits'] });
    },
  });

  if (activeCountId) {
    return (
      <div className="flex min-h-full flex-col p-2 pb-8" data-theme="warehouse">
        <CycleCountScanner
          cycleCountId={activeCountId}
          onBack={() => setActiveCountId(null)}
          onComplete={() => {
            void queryClient.invalidateQueries({ queryKey: ['cycle-counts'] });
          }}
        />
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col gap-6 p-4 pb-8" data-theme="warehouse">
      <div className="text-center">
        <ClipboardList className="mx-auto h-8 w-8 text-accent" />
        <h1 className="mt-2 text-2xl font-bold text-text">Cycle counts</h1>
        <p className="text-sm text-text-muted">
          Blind counting with automated variance escalation
        </p>
      </div>

      {canReview && (
        <Card data-testid="pending-variances-card">
          <CardHeader
            title="Pending Variances"
            description="Financial impact above the auto-adjust threshold — manager review required"
          />
          {pendingLoading ? (
            <TableSkeleton rows={3} cols={6} />
          ) : pending.length === 0 ? (
            <p className="py-8 text-center text-sm text-text-muted" data-testid="pending-variances-empty">
              No variances awaiting review.
            </p>
          ) : (
            <PendingVariancesTable
              rows={pending}
              busyId={busyId}
              onApprove={(id) => approve.mutate(id)}
              onRecount={(id) => recount.mutate(id)}
            />
          )}
        </Card>
      )}

      <Card>
        <CardHeader
          title="Priority audits"
          description="Bins flagged by velocity or adjustment patterns — count these first"
        />
        {isLoading ? (
          <TableSkeleton rows={4} cols={4} />
        ) : audits.length === 0 ? (
          <p className="py-8 text-center text-sm text-text-muted">No priority audits right now.</p>
        ) : (
          <PriorityAuditsTable audits={audits} onOpen={setActiveCountId} />
        )}
      </Card>
    </div>
  );
}

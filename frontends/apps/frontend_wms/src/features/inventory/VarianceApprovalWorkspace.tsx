import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Scale } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PendingVariance } from '@/api/types';
import { RequireRole } from '@/components/auth/RequireRole';
import { AlertDialog } from '@/components/ui/AlertDialog';
import { Button } from '@/components/ui/Button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState } from '@/components/layout/ListPageState';
import { useToast } from '@/components/ui/Toast';
import { cn } from '@/lib/utils';

function money(value: number | string): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value);
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(n);
}

export function VarianceApprovalWorkspace() {
  const { lineId } = useParams<{ lineId?: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const pendingQuery = useQuery({
    queryKey: ['cycle-counts', 'pending-variances'],
    queryFn: async () => (await apiClient.get<PendingVariance[]>('/api/v1/cycle-counts/pending-variances')).data,
  });

  const rows = pendingQuery.data ?? [];
  const focused = lineId ? rows.find((row) => row.lineId === lineId) ?? null : null;

  const approve = useMutation({
    mutationFn: async (id: string) => {
      setBusyId(id);
      await apiClient.post(`/api/v1/cycle-counts/lines/${id}/approve-adjustment`);
    },
    onSuccess: async () => {
      toast('Variance approved. A stock correction is on the ledger.', { tone: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts'] });
    },
    onError: () => toast('Could not approve this variance.', { tone: 'danger' }),
    onSettled: () => setBusyId(null),
  });

  const recount = useMutation({
    mutationFn: async (id: string) => {
      setBusyId(id);
      await apiClient.post(`/api/v1/cycle-counts/lines/${id}/request-recount`);
    },
    onSuccess: async () => {
      toast('Recount sent back to the floor.', { tone: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts'] });
    },
    onError: () => toast('Could not request a recount.', { tone: 'danger' }),
    onSettled: () => setBusyId(null),
  });

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="variance-workspace">
      <header className="shrink-0 border-b border-border/60 px-6 py-4">
        <Link
          to="/cycle-counts"
          className="inline-flex items-center gap-1.5 text-sm text-text-muted transition-colors hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Cycle counts
        </Link>
        <div className="mt-3 flex items-start gap-3">
          <Scale className="mt-1 h-5 w-5 text-text-muted" aria-hidden />
          <div>
            <h1 className="text-2xl font-bold text-text">Variance approval</h1>
            <p className="mt-1 text-sm text-text-muted">
              Blind counts that miss the ledger become a variance. weGrowStock never overwrites on-hand until a
              manager posts the correction.
            </p>
          </div>
        </div>
      </header>

      <div className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <ListPageState
          isLoading={pendingQuery.isLoading}
          isError={pendingQuery.isError}
          error={pendingQuery.error}
          data={rows}
          refetch={() => void pendingQuery.refetch()}
          emptyTitle="No variances awaiting review"
          emptyDescription="Counts inside the auto-adjust threshold close themselves. Over-threshold misses land here."
          emptyTestId="variance-workspace-empty"
        >
          {(items) => (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Location</TableHead>
                  <TableHead>SKU</TableHead>
                  <TableHead align="right">Expected Quantity</TableHead>
                  <TableHead align="right">Counted Quantity</TableHead>
                  <TableHead align="right">Financial delta</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((row) => {
                  const selected = focused?.lineId === row.lineId || (!focused && lineId === row.lineId);
                  return (
                    <TableRow
                      key={row.lineId}
                      data-testid={`variance-row-${row.lineId}`}
                      className={cn(selected && 'bg-accent-muted/40')}
                    >
                      <TableCell mono>{row.locationPath}</TableCell>
                      <TableCell mono>{row.sku}</TableCell>
                      <TableCell align="right" mono data-testid={`variance-expected-${row.lineId}`}>
                        {row.expectedQty}
                      </TableCell>
                      <TableCell align="right" mono data-testid={`variance-counted-${row.lineId}`}>
                        {row.countedQty}
                      </TableCell>
                      <TableCell align="right" mono>
                        {money(row.financialDelta)}
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-2">
                          <Button
                            size="sm"
                            variant="secondary"
                            data-testid={`request-recount-${row.lineId}`}
                            loading={busyId === row.lineId}
                            onClick={() => recount.mutate(row.lineId)}
                          >
                            Request Recount
                          </Button>
                          <RequireRole roles={['WAREHOUSE_MANAGER']}>
                            <Button
                              size="sm"
                              data-testid={`approve-variance-${row.lineId}`}
                              loading={busyId === row.lineId}
                              onClick={() => setConfirmId(row.lineId)}
                            >
                              Approve Variance
                            </Button>
                          </RequireRole>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </ListPageState>
      </div>

      {confirmId ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirmId(null)}
          title="Approve this variance?"
          description="This posts a stock correction to the immutable ledger and closes the count cycle. It does not rewrite the original expected quantity."
          confirmLabel="Approve Variance"
          confirming={approve.isPending}
          onConfirm={() => {
            const id = confirmId;
            setConfirmId(null);
            approve.mutate(id, {
              onSuccess: () => {
                if (lineId) navigate('/inventory/variances');
              },
            });
          }}
        />
      ) : null}
    </div>
  );
}

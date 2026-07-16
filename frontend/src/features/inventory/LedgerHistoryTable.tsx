import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { listLedgerTransactions, type InventoryLedgerEntry } from '@/api/inventory';
import { useReverseTransactionMutation } from '@/hooks/useReverseTransactionMutation';
import { AlertDialog } from '@/components/ui/AlertDialog';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useSessionStore } from '@/stores/session';
import { formatNumber } from '@/lib/utils';

function canReverse(entry: InventoryLedgerEntry, reversedIds: Set<string>): boolean {
  if (entry.reasonCode === 'ERROR_CORRECTION') return false;
  if (entry.reversalOfLedgerId) return false;
  if (reversedIds.has(entry.id)) return false;
  return true;
}

function formatDelta(value: number | string): string {
  const n = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(n)) return String(value);
  const sign = n > 0 ? '+' : '';
  return `${sign}${formatNumber(n)}`;
}

export function LedgerHistoryTable() {
  const { toast } = useToast();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canUndo = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const [pendingId, setPendingId] = useState<string | null>(null);

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['inventory_ledger'],
    queryFn: () => listLedgerTransactions(50),
    staleTime: 0,
    retry: false,
  });

  const reverseMutation = useReverseTransactionMutation();

  const reversedIds = useMemo(() => {
    const ids = new Set<string>();
    for (const row of data) {
      if (row.reversalOfLedgerId) ids.add(row.reversalOfLedgerId);
    }
    return ids;
  }, [data]);

  const pendingEntry = pendingId ? data.find((r) => r.id === pendingId) : undefined;

  return (
    <section
      className="rounded-2xl bg-surface-raised p-5 shadow-card"
      data-testid="ledger-history-table"
    >
      <div className="mb-4">
        <h2 className="text-sm font-semibold text-text">Ledger history</h2>
        <p className="text-sm text-text-muted">
          Recent inventory movements. Reverse data-entry mistakes with a compensating adjustment.
        </p>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      ) : isError ? (
        <p className="text-sm text-danger">Could not load ledger history.</p>
      ) : data.length === 0 ? (
        <p className="text-sm text-text-muted">No ledger movements yet.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>When</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Qty</TableHead>
              <TableHead>Reason</TableHead>
              {canUndo && <TableHead className="w-14 text-right">Action</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((row) => {
              const undoable = canUndo && canReverse(row, reversedIds);
              return (
                <TableRow key={row.id} data-testid={`ledger-row-${row.id}`}>
                  <TableCell className="whitespace-nowrap text-xs text-text-muted">
                    {row.createdAt
                      ? new Date(row.createdAt).toLocaleString(undefined, {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })
                      : '—'}
                  </TableCell>
                  <TableCell className="font-mono text-xs">{row.movementType}</TableCell>
                  <TableCell className="tabular-nums text-sm">{formatDelta(row.quantityDelta)}</TableCell>
                  <TableCell className="text-xs text-text-muted">
                    {row.reasonCode ?? '—'}
                  </TableCell>
                  {canUndo && (
                    <TableCell className="text-right">
                      {undoable ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          aria-label="Reverse transaction"
                          data-testid={`reverse-ledger-${row.id}`}
                          onClick={() => setPendingId(row.id)}
                        >
                          <RotateCcw className="h-4 w-4" />
                        </Button>
                      ) : (
                        <span className="inline-block w-8" aria-hidden />
                      )}
                    </TableCell>
                  )}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}

      <AlertDialog
        open={pendingId != null}
        onOpenChange={(open) => {
          if (!open) setPendingId(null);
        }}
        title="Reverse Transaction?"
        description="This will instantly correct your inventory balances by posting a compensating adjustment. This action is recorded in the audit log."
        confirmLabel="Confirm Reversal"
        confirming={reverseMutation.isPending}
        onConfirm={() => {
          if (!pendingId) return;
          reverseMutation.mutate(pendingId, {
            onSuccess: () => {
              toast('Transaction reversed', { tone: 'success' });
              setPendingId(null);
            },
            onError: () => {
              toast('Could not reverse transaction', { tone: 'danger' });
            },
          });
        }}
      />

      {pendingEntry ? (
        <span className="sr-only">Pending reversal for {pendingEntry.movementType}</span>
      ) : null}
    </section>
  );
}

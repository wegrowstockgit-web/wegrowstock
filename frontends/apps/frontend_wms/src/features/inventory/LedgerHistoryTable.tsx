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
  VirtualizedTable,
  type VirtualizedColumnDef,
} from '@/components/ui/primitives/VirtualizedTable';
import { TableDensityScope } from '@/hooks/useDensity';
import { DataListWorkspace } from '@/components/layout/DataListWorkspace';
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

export function LedgerHistoryTable({
  variantId,
  limit = 50,
  embedded = false,
}: {
  /** When set, only show movements for this SKU/variant. */
  variantId?: string;
  limit?: number;
  /** Compact chrome for peek drawers / report panels. */
  embedded?: boolean;
}) {
  const { toast } = useToast();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canUndo = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const [pendingId, setPendingId] = useState<string | null>(null);

  const { data = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['inventory_ledger', variantId ?? 'all', limit],
    // Backend listRecentLedger caps at 100 — never fetch the full ledger into the browser.
    queryFn: () => listLedgerTransactions(limit, variantId),
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

  const columns: VirtualizedColumnDef<InventoryLedgerEntry>[] = useMemo(
    () => [
      {
        id: 'when',
        header: 'When',
        width: 160,
        sortable: true,
        sortValue: (row) => row.createdAt,
        cell: (row) =>
          row.createdAt
            ? new Date(row.createdAt).toLocaleString(undefined, {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })
            : '—',
      },
      {
        id: 'type',
        header: 'Type',
        width: 120,
        sortable: true,
        sortValue: (row) => row.movementType,
        cell: (row) => row.movementType,
      },
      {
        id: 'qty',
        header: 'Qty',
        width: 88,
        align: 'right',
        sortable: true,
        sortValue: (row) => Number(row.quantityDelta),
        cell: (row) => formatDelta(row.quantityDelta),
      },
      {
        id: 'reason',
        header: 'Reason',
        width: 160,
        flexGrow: true,
        sortable: true,
        sortValue: (row) => row.reasonCode ?? '',
        cell: (row) => row.reasonCode ?? '—',
      },
      ...(canUndo
        ? [
            {
              id: 'action',
              header: 'Action',
              width: 72,
              align: 'right' as const,
              hideable: false,
              sortable: false,
              cell: (row: InventoryLedgerEntry) =>
                canReverse(row, reversedIds) ? (
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
                ),
            } satisfies VirtualizedColumnDef<InventoryLedgerEntry>,
          ]
        : []),
    ],
    [canUndo, reversedIds],
  );

  return (
    <section
      className={
        embedded
          ? 'space-y-3'
          : 'rounded-2xl bg-surface-raised p-5 shadow-card'
      }
      data-testid="ledger-history-table"
    >
      {!embedded && (
        <div className="mb-4">
          <h2 className="text-sm font-semibold text-text">Ledger history</h2>
          <p className="text-sm text-text-muted">
            Recent inventory movements. Reverse data-entry mistakes with a compensating adjustment.
          </p>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-2" data-testid="list-page-loading">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      ) : isError ? (
        <div data-testid="list-page-error">
          <p className="text-sm text-danger">Could not load ledger history.</p>
          <Button size="sm" variant="ghost" className="mt-2" onClick={() => void refetch()}>
            Retry
          </Button>
        </div>
      ) : data.length === 0 ? (
        <p className="text-sm text-text-muted" data-testid="list-page-empty">
          {variantId ? 'No ledger movements for this item yet.' : 'No ledger movements yet.'}
        </p>
      ) : (
        <TableDensityScope gridId="ledger-history">
          <DataListWorkspace className="h-[600px]" testId="ledger-virtualized-table">
            <VirtualizedTable
              gridId="ledger-history"
              columns={columns}
              rows={data}
              getRowId={(row) => row.id}
            />
          </DataListWorkspace>
        </TableDensityScope>
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

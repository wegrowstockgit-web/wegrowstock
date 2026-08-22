import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Lock, Undo2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { SalesOrderDetail } from '@/api/types';
import { RequireRole } from '@/components/auth/RequireRole';
import { AlertDialog } from '@/components/ui/AlertDialog';
import { Button } from '@/components/ui/Button';
import { InlineEditableCell } from '@/components/ui/InlineEditableCell';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useReverseTransactionMutation } from '@/hooks/useReverseTransactionMutation';
import { useToast } from '@/components/ui/Toast';
import { cn, formatCurrency } from '@/lib/utils';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  SUBMITTED: 'bg-accent-muted text-accent',
  CONFIRMED: 'bg-accent-muted text-accent',
  ALLOCATED: 'bg-accent-muted text-accent',
  PARTIALLY_ALLOCATED: 'bg-warning/10 text-warning',
  PARTIALLY_SHIPPED: 'bg-warning/10 text-warning',
  SHIPPED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
};

type FulfillmentLedgerRow = {
  id: string;
  lineId: string;
  quantityDelta: number;
  alreadyReversed: boolean;
};

function shippedQuantity(order: SalesOrderDetail | undefined): number {
  if (!order) return 0;
  return order.lines.reduce((sum, line) => sum + Number(line.qtyShipped ?? 0), 0);
}

function allocationLabel(line: SalesOrderDetail['lines'][number]): string {
  const allocated = Number(line.qtyAllocated ?? 0);
  const ordered = Number(line.qtyOrdered ?? 0);
  if (allocated <= 0) return 'Unallocated';
  if (allocated >= ordered) return 'Allocated';
  return 'Partial';
}

export function SalesOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const reverseMutation = useReverseTransactionMutation();
  const [confirm, setConfirm] = useState<'submit' | 'allocate' | 'cancel' | 'reverse' | null>(null);

  const orderQuery = useQuery({
    queryKey: ['sales-orders', id],
    queryFn: async () => (await apiClient.get<SalesOrderDetail>(`/api/v1/sales-orders/${id}`)).data,
    enabled: !!id,
  });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
  };

  const submitMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/sales-orders/${id}/submit`),
    onSuccess: async () => {
      await invalidate();
      toast('Sales order submitted. Lines are locked.', { tone: 'success' });
    },
    onError: () => toast('Could not submit this sales order.', { tone: 'danger' }),
  });

  const allocateMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/sales-orders/${id}/allocate`),
    onSuccess: async () => {
      await invalidate();
      toast('Inventory reserved for this order.', { tone: 'success' });
    },
    onError: () => toast('Could not force allocation.', { tone: 'danger' }),
  });

  const cancelMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/sales-orders/${id}/cancel`),
    onSuccess: async () => {
      await invalidate();
      toast('Sales order cancelled. No inventory was shipped.', { tone: 'success' });
    },
    onError: () => toast('Could not cancel this sales order.', { tone: 'danger' }),
  });

  const updateLineMutation = useMutation({
    mutationFn: async ({
      lineId,
      qtyOrdered,
      unitPrice,
    }: {
      lineId: string;
      qtyOrdered?: number;
      unitPrice?: number;
    }) => apiClient.patch(`/api/v1/sales-orders/${id}/lines/${lineId}`, { qtyOrdered, unitPrice }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: () => toast('Line is locked after submit. Reverse fulfillment to correct stock.', { tone: 'danger' }),
  });

  const reverseFulfillment = async () => {
    if (!id) return;
    const { data } = await apiClient.get<FulfillmentLedgerRow[]>(`/api/v1/sales-orders/${id}/fulfillment-ledger`);
    const open = data.filter((row) => !row.alreadyReversed && Number(row.quantityDelta) < 0);
    if (open.length === 0) {
      toast('No open fulfillment entries to reverse.', { tone: 'danger' });
      return;
    }
    for (const row of open) {
      await reverseMutation.mutateAsync(row.id);
    }
    await invalidate();
    toast('Fulfillment reversed with a ledger offset. The original ship stays in history.', { tone: 'success' });
  };

  const order = orderQuery.data;
  const draft = order?.status === 'DRAFT';
  const shipped = shippedQuantity(order);
  const canCancel = !!order && order.status !== 'CANCELLED' && order.status !== 'CLOSED' && shipped === 0;
  const canReverse = shipped > 0;

  if (orderQuery.isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-text-muted" data-testid="so-workspace-loading">
        Loading sales order…
      </div>
    );
  }

  if (orderQuery.isError || !order) {
    return (
      <div className="space-y-4 p-6" data-testid="so-workspace-error">
        <p className="text-sm text-danger">This sales order could not be loaded.</p>
        <Button variant="secondary" onClick={() => navigate('/sales-orders')}>
          Back to sales orders
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="so-workspace" data-locked={draft ? 'false' : 'true'}>
      <header className="shrink-0 border-b border-border/60 px-6 py-4">
        <Link
          to="/sales-orders"
          className="inline-flex items-center gap-1.5 text-sm text-text-muted transition-colors hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Sales orders
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-bold text-text" data-testid="so-workspace-title">
                {order.number}
              </h1>
              <span
                className={cn(
                  'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                  STATUS_STYLES[order.status] ?? 'bg-surface-overlay text-text-muted',
                )}
                data-testid="so-workspace-status"
              >
                {order.status.replaceAll('_', ' ')}
              </span>
            </div>
            <p className="mt-1 text-sm text-text-muted">{order.customerName}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {draft ? (
              <Button data-testid="submit-so" onClick={() => setConfirm('submit')} loading={submitMutation.isPending}>
                Submit Order
              </Button>
            ) : null}
            {!draft && order.status !== 'CANCELLED' ? (
              <Button
                variant="secondary"
                data-testid="force-allocate-so"
                onClick={() => setConfirm('allocate')}
                loading={allocateMutation.isPending}
              >
                Force Allocation
              </Button>
            ) : null}
            {canCancel ? (
              <Button
                variant="secondary"
                data-testid="cancel-so"
                onClick={() => setConfirm('cancel')}
                loading={cancelMutation.isPending}
              >
                Cancel Order
              </Button>
            ) : null}
            {canReverse ? (
              <RequireRole roles={['WAREHOUSE_MANAGER', 'ADMIN']}>
                <Button
                  variant="danger"
                  data-testid="reverse-fulfillment"
                  onClick={() => setConfirm('reverse')}
                  loading={reverseMutation.isPending}
                >
                  <Undo2 className="h-4 w-4" aria-hidden />
                  Reverse Fulfillment
                </Button>
              </RequireRole>
            ) : null}
          </div>
        </div>
      </header>

      {!draft ? (
        <div
          className="mx-6 mt-4 flex items-start gap-3 rounded-lg border border-border bg-surface-overlay/60 px-4 py-3"
          data-testid="so-workspace-lock"
        >
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-text-muted" aria-hidden />
          <p className="text-sm text-text">
            This document is locked. weGrowStock never rewrites a posted shipment — correct a fat-fingered quantity
            with Reverse Fulfillment, which posts an offsetting ledger entry.
          </p>
        </div>
      ) : null}

      <div className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>SKU</TableHead>
              <TableHead align="right">Qty</TableHead>
              <TableHead align="right">Price</TableHead>
              <TableHead>Allocation</TableHead>
              <TableHead align="right">Shipped</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {order.lines.map((line) => (
              <TableRow key={line.id} data-testid="so-workspace-line">
                <TableCell>
                  <div>
                    <p className="font-medium text-text">{line.name ?? 'Item'}</p>
                    <p className="font-mono text-xs text-text-muted">{line.sku ?? line.variantId.slice(0, 8)}</p>
                  </div>
                </TableCell>
                <TableCell align="right">
                  {draft ? (
                    <InlineEditableCell
                      testId={`so-line-qty-${line.id}`}
                      value={line.qtyOrdered}
                      inputType="number"
                      onSave={async (value) => {
                        await updateLineMutation.mutateAsync({ lineId: line.id, qtyOrdered: Number(value) });
                      }}
                    />
                  ) : (
                    <span className="font-mono tabular-nums" data-testid={`so-line-qty-locked-${line.id}`}>
                      {line.qtyOrdered}
                    </span>
                  )}
                </TableCell>
                <TableCell align="right">
                  {draft ? (
                    <InlineEditableCell
                      testId={`so-line-price-${line.id}`}
                      value={line.unitPrice}
                      inputType="number"
                      formatDisplay={(value) => formatCurrency(Number(value))}
                      onSave={async (value) => {
                        await updateLineMutation.mutateAsync({ lineId: line.id, unitPrice: Number(value) });
                      }}
                    />
                  ) : (
                    <span className="font-mono tabular-nums" data-testid={`so-line-price-locked-${line.id}`}>
                      {formatCurrency(Number(line.unitPrice))}
                    </span>
                  )}
                </TableCell>
                <TableCell>
                  <span className="text-sm text-text-muted" data-testid={`so-line-alloc-${line.id}`}>
                    {allocationLabel(line)}
                  </span>
                </TableCell>
                <TableCell align="right">
                  <span className="font-mono tabular-nums text-text-muted">{line.qtyShipped}</span>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {confirm === 'submit' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Submit sales order?"
          description="After submit, quantities and prices lock. weGrowStock will not silently rewrite this document."
          confirmLabel="Submit Order"
          confirming={submitMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            submitMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'allocate' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Force allocation?"
          description="weGrowStock will reserve on-hand inventory for these lines."
          confirmLabel="Force Allocation"
          confirming={allocateMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            allocateMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'cancel' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Cancel sales order?"
          description="No stock has shipped, so this voids the document cleanly."
          confirmLabel="Cancel Order"
          confirming={cancelMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            cancelMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'reverse' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Reverse fulfillment?"
          description="This posts a negative offset on the immutable ledger. The original ship remains in history."
          confirmLabel="Reverse Fulfillment"
          confirming={reverseMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            void reverseFulfillment().catch(() =>
              toast('Could not reverse fulfillment. Check manager access and try again.', { tone: 'danger' }),
            );
          }}
        />
      ) : null}
    </div>
  );
}

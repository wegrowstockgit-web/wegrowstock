import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Lock, Network, Plus, Truck, Undo2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PaginatedResponse, ProductVariant, PurchaseOrderDetail } from '@/api/types';
import { RequireRole } from '@/components/auth/RequireRole';
import { AlertDialog } from '@/components/ui/AlertDialog';
import { Button } from '@/components/ui/Button';
import { InlineEditableCell } from '@/components/ui/InlineEditableCell';
import { Select } from '@/components/ui/Select';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { formatMediumDate } from '@/lib/utils';
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
import { cn } from '@/lib/utils';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  SUBMITTED: 'bg-accent-muted text-accent',
  IN_TRANSIT: 'bg-accent-muted text-accent',
  PARTIALLY_RECEIVED: 'bg-warning/10 text-warning',
  RECEIVED: 'bg-success/10 text-success',
  CLOSED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
};

type ReceiptLedgerRow = {
  id: string;
  lineId: string;
  quantityDelta: number;
  alreadyReversed: boolean;
};

function receivedQuantity(po: PurchaseOrderDetail | undefined): number {
  if (!po) return 0;
  return po.lines.reduce((sum, line) => sum + Number(line.qtyReceived ?? 0), 0);
}

export function PurchaseOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const reverseMutation = useReverseTransactionMutation();
  const [confirm, setConfirm] = useState<'submit' | 'cancel' | 'reverse' | 'revert' | null>(null);
  const [transitOpen, setTransitOpen] = useState(false);
  const [transitVendorRef, setTransitVendorRef] = useState('');
  const [transitTracking, setTransitTracking] = useState('');
  const [transitEta, setTransitEta] = useState('');
  const [newVariantId, setNewVariantId] = useState('');
  const [newQty, setNewQty] = useState('1');
  const [newCost, setNewCost] = useState('');

  const poQuery = useQuery({
    queryKey: ['purchase-orders', id],
    queryFn: async () => (await apiClient.get<PurchaseOrderDetail>(`/api/v1/purchase-orders/${id}`)).data,
    enabled: !!id,
  });

  const { data: variantsPage } = useQuery({
    queryKey: ['variants', 'all'],
    queryFn: async () =>
      (await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants?limit=200')).data,
  });
  const variants = variantsPage?.items ?? [];
  const skuById = useMemo(
    () => Object.fromEntries(variants.map((variant) => [variant.id, `${variant.sku} — ${variant.name}`])),
    [variants],
  );

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
  };

  const submitMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/purchase-orders/${id}/submit`),
    onSuccess: async () => {
      await invalidate();
      toast('Purchase order submitted. Lines are locked.', { tone: 'success' });
    },
    onError: () => toast('Could not submit this purchase order.', { tone: 'danger' }),
  });

  const transitMutation = useMutation({
    mutationFn: async () =>
      apiClient.post(`/api/v1/purchase-orders/${id}/mark-in-transit`, {
        vendorReference: transitVendorRef.trim() || undefined,
        trackingNumber: transitTracking.trim() || undefined,
        expectedDeliveryDate: transitEta ? `${transitEta}T12:00:00Z` : undefined,
      }),
    onSuccess: async () => {
      setTransitOpen(false);
      setTransitVendorRef('');
      setTransitTracking('');
      setTransitEta('');
      await invalidate();
      toast('Purchase order marked in transit.', { tone: 'success' });
    },
    onError: () => toast('Could not mark this purchase order in transit.', { tone: 'danger' }),
  });

  const revertMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/purchase-orders/${id}/revert-to-submitted`),
    onSuccess: async () => {
      await invalidate();
      toast('Purchase order returned to Submitted. Tracking cleared.', { tone: 'success' });
    },
    onError: () => toast('Could not revert this purchase order.', { tone: 'danger' }),
  });

  const cancelMutation = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/purchase-orders/${id}/cancel`),
    onSuccess: async () => {
      await invalidate();
      toast('Purchase order cancelled. No inventory was touched.', { tone: 'success' });
    },
    onError: () => toast('Could not cancel this purchase order.', { tone: 'danger' }),
  });

  const updateLineMutation = useMutation({
    mutationFn: async ({
      lineId,
      qtyOrdered,
      unitCost,
    }: {
      lineId: string;
      qtyOrdered?: number;
      unitCost?: number;
    }) => apiClient.patch(`/api/v1/purchase-orders/${id}/lines/${lineId}`, { qtyOrdered, unitCost }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: () => toast('Line is locked after submit. Reverse a receipt to correct stock.', { tone: 'danger' }),
  });

  const addLineMutation = useMutation({
    mutationFn: async () =>
      apiClient.post(`/api/v1/purchase-orders/${id}/lines`, {
        variantId: newVariantId,
        qtyOrdered: Number(newQty),
        unitCost: newCost ? Number(newCost) : undefined,
      }),
    onSuccess: async () => {
      setNewVariantId('');
      setNewQty('1');
      setNewCost('');
      await invalidate();
      toast('Line added.', { tone: 'success' });
    },
    onError: () => toast('Could not add that item to a locked order.', { tone: 'danger' }),
  });

  const reverseReceipts = async () => {
    if (!id) return;
    const { data } = await apiClient.get<ReceiptLedgerRow[]>(`/api/v1/purchase-orders/${id}/receipt-ledger`);
    const open = data.filter((row) => !row.alreadyReversed && Number(row.quantityDelta) > 0);
    if (open.length === 0) {
      toast('No open receipts to reverse.', { tone: 'danger' });
      return;
    }
    for (const row of open) {
      await reverseMutation.mutateAsync(row.id);
    }
    await apiClient.post(`/api/v1/purchase-orders/${id}/sync-receipts`);
    await invalidate();
    toast('Receipt reversed with a ledger offset. The original receive stays in history.', { tone: 'success' });
  };

  const po = poQuery.data;
  const draft = po?.status === 'DRAFT';
  const submitted = po?.status === 'SUBMITTED';
  const inTransit = po?.status === 'IN_TRANSIT';
  const meshPartner = Boolean(po?.isMeshPartner);
  const received = receivedQuantity(po);
  const canCancel = submitted && received === 0;
  const canMarkInTransit = submitted;
  const canRevert = inTransit && !meshPartner && received === 0;
  const canReverse = received > 0;
  const trackingNumber = po?.trackingNumber || null;
  const carrier = po?.carrier || null;

  if (poQuery.isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-text-muted" data-testid="po-workspace-loading">
        Loading purchase order…
      </div>
    );
  }

  if (poQuery.isError || !po) {
    return (
      <div className="space-y-4 p-6" data-testid="po-workspace-error">
        <p className="text-sm text-danger">This purchase order could not be loaded.</p>
        <Button variant="secondary" onClick={() => navigate('/purchase-orders')}>
          Back to purchase orders
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="po-workspace" data-locked={draft ? 'false' : 'true'}>
      <header className="shrink-0 border-b border-border/60 px-6 py-4">
        <Link
          to="/purchase-orders"
          className="inline-flex items-center gap-1.5 text-sm text-text-muted transition-colors hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Purchase orders
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-bold text-text" data-testid="po-workspace-title">
                {po.number}
              </h1>
              <span
                className={cn(
                  'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                  STATUS_STYLES[po.status] ?? 'bg-surface-overlay text-text-muted',
                )}
                data-testid="po-workspace-status"
              >
                {po.status.replaceAll('_', ' ')}
              </span>
              {meshPartner ? (
                <span
                  className="inline-flex items-center gap-1 rounded-full bg-accent-muted px-2.5 py-0.5 text-xs font-medium text-accent"
                  data-testid="po-mesh-badge"
                >
                  <Network className="h-3 w-3" aria-hidden />
                  Mesh Network
                </span>
              ) : null}
            </div>
            <p className="mt-1 text-sm text-text-muted">{po.supplierName}</p>
            {trackingNumber || carrier || po.expectedAt ? (
              <p className="mt-1 font-mono text-xs text-text-muted" data-testid="po-tracking-details">
                {[
                  carrier ? `Carrier ${carrier}` : null,
                  trackingNumber ? `Tracking ${trackingNumber}` : null,
                  po.expectedAt ? `ETA ${formatMediumDate(po.expectedAt)}` : null,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            ) : null}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {draft ? (
              <Button
                data-testid="submit-po"
                onClick={() => setConfirm('submit')}
                loading={submitMutation.isPending}
              >
                Submit PO
              </Button>
            ) : null}
            {canMarkInTransit ? (
              <span title={meshPartner ? 'Automated via Mesh Network' : undefined}>
                <Button
                  data-testid="mark-in-transit"
                  disabled={meshPartner}
                  onClick={() => setTransitOpen(true)}
                  loading={transitMutation.isPending}
                >
                  <Truck className="h-4 w-4" aria-hidden />
                  Mark In Transit
                </Button>
              </span>
            ) : null}
            {canRevert ? (
              <Button
                variant="secondary"
                data-testid="revert-to-submitted"
                onClick={() => setConfirm('revert')}
                loading={revertMutation.isPending}
              >
                Revert to Submitted
              </Button>
            ) : null}
            {canCancel ? (
              <Button
                variant="secondary"
                data-testid="cancel-po"
                onClick={() => setConfirm('cancel')}
                loading={cancelMutation.isPending}
              >
                Cancel PO
              </Button>
            ) : null}
            {canReverse ? (
              <RequireRole roles={['WAREHOUSE_MANAGER', 'ADMIN']}>
                <Button
                  variant="danger"
                  data-testid="reverse-receipt"
                  onClick={() => setConfirm('reverse')}
                  loading={reverseMutation.isPending}
                >
                  <Undo2 className="h-4 w-4" aria-hidden />
                  Reverse Receipt
                </Button>
              </RequireRole>
            ) : null}
          </div>
        </div>
      </header>

      {meshPartner && (submitted || inTransit) ? (
        <div
          className="mx-6 mt-4 rounded-lg border border-accent/30 bg-accent-muted/40 px-4 py-3 text-sm text-text"
          data-testid="po-mesh-automation-banner"
        >
          Automated via Mesh Network: This PO will automatically update to IN TRANSIT and populate tracking
          details as soon as the supplier ships the order.
        </div>
      ) : null}

      {!draft ? (
        <div
          className="mx-6 mt-4 inline-flex items-center gap-2 self-start rounded-md border border-border bg-surface-overlay/60 px-3 py-1.5"
          data-testid="po-workspace-lock"
        >
          <Lock className="h-3.5 w-3.5 text-text-muted" aria-hidden />
          <span className="text-xs font-medium uppercase tracking-wide text-text-muted">Locked</span>
        </div>
      ) : null}

      <div className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>SKU</TableHead>
              <TableHead align="right">Quantity</TableHead>
              <TableHead align="right">Unit cost</TableHead>
              <TableHead align="right">Received</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {po.lines.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} className="text-text-muted">
                  No lines yet. Add an item to this draft.
                </TableCell>
              </TableRow>
            ) : (
              po.lines.map((line) => (
                <TableRow key={line.id} data-testid="po-workspace-line">
                  <TableCell>
                    <span className="font-mono text-sm">
                      {skuById[line.variantId] ?? line.variantId.slice(0, 8)}
                    </span>
                  </TableCell>
                  <TableCell align="right">
                    {draft ? (
                      <InlineEditableCell
                        testId={`po-line-qty-${line.id}`}
                        value={line.qtyOrdered}
                        inputType="number"
                        onSave={async (value) => {
                          await updateLineMutation.mutateAsync({ lineId: line.id, qtyOrdered: Number(value) });
                        }}
                      />
                    ) : (
                      <span className="font-mono tabular-nums" data-testid={`po-line-qty-locked-${line.id}`}>
                        {line.qtyOrdered}
                      </span>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {draft ? (
                      <InlineEditableCell
                        testId={`po-line-cost-${line.id}`}
                        value={line.unitCost}
                        inputType="number"
                        formatDisplay={(value) => Number(value).toFixed(2)}
                        onSave={async (value) => {
                          await updateLineMutation.mutateAsync({ lineId: line.id, unitCost: Number(value) });
                        }}
                      />
                    ) : (
                      <span className="font-mono tabular-nums" data-testid={`po-line-cost-locked-${line.id}`}>
                        {Number(line.unitCost).toFixed(2)}
                      </span>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <span className="font-mono tabular-nums text-text-muted">{line.qtyReceived}</span>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>

        {draft ? (
          <div
            className="mt-4 flex flex-wrap items-end gap-3 rounded-lg border border-dashed border-border px-4 py-3"
            data-testid="po-add-item"
          >
            <div className="min-w-[16rem] flex-1">
              <Select
                label="Add item"
                value={newVariantId}
                onChange={(e) => setNewVariantId(e.target.value)}
                data-testid="po-add-sku"
              >
                <option value="">Select SKU…</option>
                {variants.map((variant) => (
                  <option key={variant.id} value={variant.id}>
                    {variant.sku} — {variant.name}
                  </option>
                ))}
              </Select>
            </div>
            <div className="w-24">
              <Input
                label="Qty"
                type="number"
                min="1"
                value={newQty}
                onChange={(e) => setNewQty(e.target.value)}
                data-testid="po-add-qty"
              />
            </div>
            <div className="w-28">
              <Input
                label="Unit cost"
                type="number"
                min="0"
                step="0.01"
                value={newCost}
                onChange={(e) => setNewCost(e.target.value)}
                data-testid="po-add-cost"
              />
            </div>
            <Button
              type="button"
              variant="secondary"
              data-testid="po-add-item-btn"
              disabled={!newVariantId || Number(newQty) <= 0}
              loading={addLineMutation.isPending}
              onClick={() => addLineMutation.mutate()}
            >
              <Plus className="h-4 w-4" aria-hidden />
              Add Item
            </Button>
          </div>
        ) : null}
      </div>

      {confirm === 'submit' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Submit purchase order?"
          description="After submit, quantities and costs lock. weGrowStock will not silently rewrite this document."
          confirmLabel="Submit PO"
          confirming={submitMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            submitMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'cancel' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Cancel purchase order?"
          description="No stock has hit the dock, so this voids the document cleanly. History stays visible."
          confirmLabel="Cancel PO"
          confirming={cancelMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            cancelMutation.mutate();
          }}
        />
      ) : null}
      {transitOpen ? (
        <Modal
          open
          onClose={() => setTransitOpen(false)}
          title="Mark in transit"
          description="Optional tracking helps the dock anticipate the freight."
        >
          <form
            className="space-y-4"
            data-testid="mark-in-transit-modal"
            onSubmit={(e) => {
              e.preventDefault();
              transitMutation.mutate();
            }}
          >
            <Input
              label="Vendor reference"
              value={transitVendorRef}
              onChange={(e) => setTransitVendorRef(e.target.value)}
              data-testid="mark-in-transit-vendor-ref"
            />
            <Input
              label="Tracking number"
              value={transitTracking}
              onChange={(e) => setTransitTracking(e.target.value)}
              data-testid="mark-in-transit-tracking"
            />
            <Input
              label="Expected delivery date"
              type="date"
              value={transitEta}
              onChange={(e) => setTransitEta(e.target.value)}
              data-testid="mark-in-transit-eta"
            />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setTransitOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" data-testid="mark-in-transit-confirm" loading={transitMutation.isPending}>
                Mark In Transit
              </Button>
            </div>
          </form>
        </Modal>
      ) : null}
      {confirm === 'revert' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Revert to Submitted?"
          description="This clears the tracking number and puts the purchase order back in the waiting queue."
          confirmLabel="Revert to Submitted"
          confirming={revertMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            revertMutation.mutate();
          }}
        />
      ) : null}
      {confirm === 'reverse' ? (
        <AlertDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title="Reverse receipt?"
          description="This posts a negative offset on the immutable ledger. The original receive remains in history."
          confirmLabel="Reverse Receipt"
          confirming={reverseMutation.isPending}
          onConfirm={() => {
            setConfirm(null);
            void reverseReceipts().catch(() =>
              toast('Could not reverse the receipt. Check manager access and try again.', { tone: 'danger' }),
            );
          }}
        />
      ) : null}
    </div>
  );
}

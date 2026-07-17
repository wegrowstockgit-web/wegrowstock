import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, Plus, RotateCcw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type { Return, ReturnLine, SalesOrder, SalesOrderDetail } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState, useListQuery } from '@/components/layout/ListPageState';
import { DensityToggle } from '@/components/ui/DensityToggle';
import { useClientSort } from '@/hooks/useClientSort';
import { useSessionStore } from '@/stores/session';
import { cn } from '@/lib/utils';

const STATUSES = [
  'ALL',
  'PENDING_REVIEW',
  'REQUESTED',
  'APPROVED',
  'RECEIVED',
  'CLOSED',
  'REJECTED',
] as const;
const DISPOSITIONS = ['RESTOCK', 'SCRAP', 'REPAIR'] as const;

const RETURNABLE_STATUSES = ['SHIPPED', 'PARTIALLY_SHIPPED', 'CLOSED'];

function ReturnsCreateModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [salesOrderId, setSalesOrderId] = useState('');
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [error, setError] = useState('');

  const { data: orders = [] } = useQuery({
    queryKey: ['sales-orders'],
    queryFn: async () => (await apiClient.get<SalesOrder[]>('/api/v1/sales-orders')).data,
    enabled: open,
  });

  const returnable = orders.filter((o) => RETURNABLE_STATUSES.includes(o.status));

  const { data: orderDetail } = useQuery({
    queryKey: ['sales-orders', salesOrderId],
    queryFn: async () =>
      (await apiClient.get<SalesOrderDetail>(`/api/v1/sales-orders/${salesOrderId}`)).data,
    enabled: open && !!salesOrderId,
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (!orderDetail) return;
      const lines = orderDetail.lines
        .filter((line) => Number(quantities[line.id] ?? 0) > 0)
        .map((line) => ({
          salesOrderLineId: line.id,
          quantityExpected: Number(quantities[line.id]),
        }));
      await apiClient.post('/api/v1/returns', { salesOrderId, lines });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
      setSalesOrderId('');
      setQuantities({});
      onClose();
    },
    onError: () => setError('Could not create the RMA. Check quantities vs shipped amounts.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="New RMA" description="Create a return from a shipped order">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Select
          label="Sales order"
          value={salesOrderId}
          onChange={(e) => {
            setSalesOrderId(e.target.value);
            setQuantities({});
          }}
          required
        >
          <option value="" disabled>
            {returnable.length === 0 ? 'No shipped orders available' : 'Select order…'}
          </option>
          {returnable.map((o) => (
            <option key={o.id} value={o.id}>
              {o.number} — {o.customerName} ({o.status})
            </option>
          ))}
        </Select>

        {orderDetail && (
          <div className="space-y-2">
            <p className="text-sm font-medium text-text">Lines to return</p>
            {orderDetail.lines.map((line) => {
              const max = Number(line.qtyShipped);
              if (max <= 0) return null;
              return (
                <div key={line.id} className="flex items-center gap-3">
                  <span className="flex-1 font-mono text-sm">{line.variantId.slice(0, 8)}…</span>
                  <span className="text-xs text-text-muted">shipped {max}</span>
                  <Input
                    aria-label="Return quantity"
                    type="number"
                    min="0"
                    max={max}
                    className="w-20"
                    value={quantities[line.id] ?? ''}
                    onChange={(e) =>
                      setQuantities((prev) => ({ ...prev, [line.id]: e.target.value }))
                    }
                  />
                </div>
              );
            })}
          </div>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            loading={mutation.isPending}
            disabled={!salesOrderId || !orderDetail?.lines.some((l) => Number(quantities[l.id]) > 0)}
          >
            Create RMA
          </Button>
        </div>
      </form>
    </Modal>
  );
}

const STATUS_STYLES: Record<string, string> = {
  REQUESTED: 'bg-warning/20 text-warning',
  PENDING_REVIEW: 'bg-warning/30 text-warning',
  APPROVED: 'bg-accent-muted text-accent',
  RECEIVED: 'bg-success/20 text-success',
  CLOSED: 'bg-surface-overlay text-text-muted',
  REJECTED: 'bg-danger/20 text-danger',
};

function RmaReviewQueue({ canManage }: { canManage: boolean }) {
  const queryClient = useQueryClient();
  const { data: pending = [], isLoading } = useQuery({
    queryKey: ['returns', 'PENDING_REVIEW'],
    queryFn: async () =>
      (await apiClient.get<Return[]>('/api/v1/returns?status=PENDING_REVIEW')).data,
    retry: false,
  });

  const reviewMutation = useMutation({
    mutationFn: async ({
      id,
      action,
    }: {
      id: string;
      action: 'approve-with-label' | 'approve-without-label' | 'deny';
    }) => {
      await apiClient.post(`/api/v1/returns/${id}/review/${action}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
    },
  });

  if (isLoading || pending.length === 0) {
    return null;
  }

  return (
    <Card className="mb-6" data-testid="rma-review-queue">
      <CardHeader
        title="RMA Review Queue"
        description={`${pending.length} portal return${pending.length === 1 ? '' : 's'} awaiting decision`}
      />
      <div className="space-y-4">
        {pending.map((rma) => (
          <div
            key={rma.id}
            className="rounded-md border border-border bg-surface-raised p-4"
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-mono font-semibold text-text">{rma.number}</p>
                <p className="text-sm text-text-muted">
                  {rma.customerName ?? 'Customer'} · {rma.salesOrderNumber ?? rma.salesOrderId}
                </p>
                <p className="mt-1 text-sm text-text">
                  Reason: <span className="font-medium">{rma.reasonCode ?? '—'}</span>
                </p>
                <p className="mt-1 text-sm text-text-muted">
                  Est. return label cost:{' '}
                  <span className="font-mono font-semibold text-text">
                    {Number(rma.estimatedLabelCost ?? 0).toLocaleString(undefined, {
                      style: 'currency',
                      currency: 'USD',
                    })}
                  </span>
                </p>
              </div>
              {canManage && (
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    loading={reviewMutation.isPending}
                    onClick={() =>
                      reviewMutation.mutate({ id: rma.id, action: 'approve-with-label' })
                    }
                  >
                    Approve & Buy Label
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={reviewMutation.isPending}
                    onClick={() =>
                      reviewMutation.mutate({ id: rma.id, action: 'approve-without-label' })
                    }
                  >
                    Approve without Label
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    loading={reviewMutation.isPending}
                    onClick={() => reviewMutation.mutate({ id: rma.id, action: 'deny' })}
                  >
                    Deny & Close
                  </Button>
                </div>
              )}
            </div>
            {(rma.evidenceUrls?.length ?? 0) > 0 && (
              <div className="mt-3">
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">
                  Evidence photos
                </p>
                <div className="flex flex-wrap gap-2">
                  {rma.evidenceUrls!.map((url) => (
                    <AuthenticatedImage
                      key={url}
                      src={url}
                      alt="RMA evidence"
                      className="h-24 w-24 rounded-md border border-border object-cover"
                    />
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </Card>
  );
}

function DispositionSelect({
  line,
  returnId,
}: {
  line: ReturnLine;
  returnId: string;
}) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: async (disposition: string) => {
      await apiClient.put(`/api/v1/returns/${returnId}/lines/${line.id}`, { disposition });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
    },
  });

  return (
    <select
      value={line.disposition ?? ''}
      onChange={(e) => mutation.mutate(e.target.value)}
      disabled={mutation.isPending}
      className="h-8 rounded-md border border-border bg-surface-raised px-2 text-sm text-text"
    >
      <option value="">Set disposition</option>
      {DISPOSITIONS.map((d) => (
        <option key={d} value={d}>
          {d}
        </option>
      ))}
    </select>
  );
}

function ReturnLinesTable({ lines, returnId }: { lines: ReturnLine[]; returnId: string }) {
  const { sort, toggle, sorted } = useClientSort(
    lines,
    {
      sku: (line) => line.sku ?? line.productName ?? line.id,
      expected: (line) => line.quantityExpected,
      received: (line) => line.quantityReceived,
      disposition: (line) => line.disposition ?? '',
    },
    { key: 'sku', dir: 'asc' },
  );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="sku" sort={sort} onSort={toggle}>
            SKU
          </TableHead>
          <TableHead sortable sortKey="expected" sort={sort} onSort={toggle}>
            Expected
          </TableHead>
          <TableHead sortable sortKey="received" sort={sort} onSort={toggle}>
            Received
          </TableHead>
          <TableHead sortable sortKey="disposition" sort={sort} onSort={toggle}>
            Disposition
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((line) => (
          <TableRow key={line.id}>
            <TableCell mono>{line.sku ?? line.productName ?? line.id}</TableCell>
            <TableCell mono>{line.quantityExpected}</TableCell>
            <TableCell mono>{line.quantityReceived}</TableCell>
            <TableCell>
              <DispositionSelect line={line} returnId={returnId} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function ReturnsPage() {
  const navigate = useNavigate();
  const canManage = useSessionStore((s) => s.canManageInventory());
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const url =
    statusFilter === 'ALL'
      ? '/api/v1/returns'
      : `/api/v1/returns?status=${statusFilter}`;

  const { data, isLoading, isError, error, refetch } = useListQuery<Return>(
    ['returns', statusFilter],
    url
  );

  return (
    <div className="p-6">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Returns (RMA)</h1>
          <p className="mt-1 text-sm text-text-muted">Manage return authorizations and dispositions</p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => navigate('/returns/receive')}>
            Receive terminal
          </Button>
          {canManage && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" />
              New RMA
            </Button>
          )}
        </div>
      </div>

      <RmaReviewQueue canManage={canManage} />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
        {STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            onClick={() => setStatusFilter(status)}
            className={cn(
              'rounded-full px-3 py-1 text-sm font-medium transition-colors',
              statusFilter === status
                ? 'bg-accent text-text-inverse'
                : 'bg-surface-overlay text-text-muted hover:text-text'
            )}
          >
            {status === 'ALL' ? 'All' : status}
          </button>
        ))}
        </div>
        <DensityToggle />
      </div>

      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={data}
        refetch={refetch}
        emptyIcon={RotateCcw}
        emptyTitle="No returns"
        emptyDescription="Return requests will appear here for approval and processing."
        emptyAction={
          canManage ? (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" />
              Create RMA
            </Button>
          ) : undefined
        }
      >
        {(returns) => (
          <div className="space-y-4">
            {returns.map((rma) => (
              <Card key={rma.id} padding="none">
                <button
                  type="button"
                  className="flex w-full items-center justify-between p-4 text-left hover:bg-surface-overlay"
                  onClick={() => setExpandedId(expandedId === rma.id ? null : rma.id)}
                >
                  <div className="flex items-center gap-4">
                    <span className="font-mono font-semibold text-text">{rma.number}</span>
                    <span className="text-sm text-text-muted">
                      {rma.customerName ?? rma.salesOrderNumber ?? rma.salesOrderId}
                    </span>
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-xs font-medium',
                        STATUS_STYLES[rma.status] ?? 'bg-surface-overlay text-text-muted'
                      )}
                    >
                      {rma.status}
                    </span>
                  </div>
                  {rma.returnLabelUrl && (
                    <a
                      href={rma.returnLabelUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      className="flex items-center gap-1 text-sm text-accent hover:underline"
                    >
                      <Download className="h-4 w-4" />
                      Label
                    </a>
                  )}
                </button>

                {expandedId === rma.id && (rma.lines?.length ?? 0) > 0 && (
                  <div className="border-t border-border p-4">
                    <CardHeader title="Line items" description="Set disposition per line" />
                    <ReturnLinesTable lines={rma.lines ?? []} returnId={rma.id} />
                  </div>
                )}
              </Card>
            ))}
          </div>
        )}
      </ListPageState>
      <ReturnsCreateModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  );
}

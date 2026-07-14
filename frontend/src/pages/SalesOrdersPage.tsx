import { useMemo, useState, type MouseEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShoppingCart, Plus, Trash2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Customer, ProductVariant, SalesOrder, SalesOrderDetail, PaginatedResponse, TenantLocation } from '@/api/types';
import { cn, formatCurrency } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import { SavedFilterViews } from '@/components/ui/SavedFilterViews';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState, useListQuery } from '@/components/layout/ListPageState';
import { useSessionStore } from '@/stores/session';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  CONFIRMED: 'bg-accent-muted text-accent',
  ALLOCATED: 'bg-accent-muted text-accent',
  PARTIALLY_SHIPPED: 'bg-warning/10 text-warning',
  SHIPPED: 'bg-success/10 text-success',
  CLOSED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
};

function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
        STATUS_STYLES[status] ?? 'bg-surface-overlay text-text-muted'
      )}
    >
      {status.replaceAll('_', ' ')}
    </span>
  );
}

interface DraftLine {
  variantId: string;
  qtyOrdered: string;
  unitPrice: string;
}

function CreateOrderModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [customerId, setCustomerId] = useState('');
  const [sourceLocationId, setSourceLocationId] = useState('');
  const [customerPoNumber, setCustomerPoNumber] = useState('');
  const [requestedShipDate, setRequestedShipDate] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([{ variantId: '', qtyOrdered: '1', unitPrice: '' }]);
  const [error, setError] = useState('');

  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: async () => (await apiClient.get<Customer[]>('/api/v1/customers')).data,
    enabled: open,
  });

  const { data: warehouses = [] } = useQuery({
    queryKey: ['locations', 'warehouse'],
    queryFn: async () =>
      (await apiClient.get<TenantLocation[]>('/api/v1/locations', { params: { type: 'WAREHOUSE' } })).data,
    enabled: open,
  });

  const { data: variantsPage } = useQuery({
    queryKey: ['variants', 'all'],
    queryFn: async () =>
      (await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants?limit=200')).data,
    enabled: open,
  });
  const variants = variantsPage?.items ?? [];

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/sales-orders', {
        customerId,
        number: `SO-${Date.now()}`,
        channel: 'DIRECT',
        sourceLocationId: sourceLocationId || undefined,
        customerPoNumber: customerPoNumber || undefined,
        requestedShipDate: requestedShipDate ? new Date(requestedShipDate).toISOString() : undefined,
        lines: lines
          .filter((l) => l.variantId && Number(l.qtyOrdered) > 0)
          .map((l) => ({
            variantId: l.variantId,
            qtyOrdered: Number(l.qtyOrdered),
            unitPrice: l.unitPrice ? Number(l.unitPrice) : undefined,
          })),
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setCustomerId('');
      setSourceLocationId('');
      setCustomerPoNumber('');
      setRequestedShipDate('');
      setLines([{ variantId: '', qtyOrdered: '1', unitPrice: '' }]);
      onClose();
    },
    onError: () => setError('Could not create the order. Check the fields and try again.'),
  });

  const updateLine = (index: number, patch: Partial<DraftLine>) => {
    setLines((prev) => prev.map((l, i) => (i === index ? { ...l, ...patch } : l)));
  };

  const validLines = lines.filter((l) => l.variantId && Number(l.qtyOrdered) > 0);

  return (
    <Modal open={open} onClose={onClose} title="New sales order" description="Order number is assigned automatically">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Select
          label="Customer"
          value={customerId}
          onChange={(e) => setCustomerId(e.target.value)}
          required
        >
          <option value="" disabled>
            Select a customer…
          </option>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>

        <Select
          label="Ship-from warehouse"
          value={sourceLocationId}
          onChange={(e) => setSourceLocationId(e.target.value)}
        >
          <option value="">Default warehouse</option>
          {warehouses.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </Select>

        <Input
          label="Customer PO number"
          value={customerPoNumber}
          onChange={(e) => setCustomerPoNumber(e.target.value)}
          placeholder="Customer reference"
        />

        <Input
          label="Requested ship date"
          type="date"
          value={requestedShipDate}
          onChange={(e) => setRequestedShipDate(e.target.value)}
        />

        <div className="space-y-3">
          <p className="text-sm font-medium text-text">Lines</p>
          {lines.map((line, index) => (
            <div key={index} className="flex items-end gap-2">
              <div className="flex-1">
                <Select
                  aria-label="Product variant"
                  value={line.variantId}
                  onChange={(e) => updateLine(index, { variantId: e.target.value })}
                  required
                >
                  <option value="" disabled>
                    Select item…
                  </option>
                  {variants.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.sku} — {v.name}
                    </option>
                  ))}
                </Select>
              </div>
              <div className="w-20">
                <Input
                  aria-label="Quantity"
                  type="number"
                  min="1"
                  value={line.qtyOrdered}
                  onChange={(e) => updateLine(index, { qtyOrdered: e.target.value })}
                  required
                />
              </div>
              <div className="w-24">
                <Input
                  aria-label="Unit price"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="Price"
                  value={line.unitPrice}
                  onChange={(e) => updateLine(index, { unitPrice: e.target.value })}
                />
              </div>
              {lines.length > 1 && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  aria-label="Remove line"
                  onClick={() => setLines((prev) => prev.filter((_, i) => i !== index))}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              )}
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => setLines((prev) => [...prev, { variantId: '', qtyOrdered: '1', unitPrice: '' }])}
          >
            <Plus className="h-4 w-4" />
            Add line
          </Button>
        </div>

        {error && <p className="text-sm text-danger">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending} disabled={!customerId || validLines.length === 0}>
            Create order
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function RowActions({ order }: { order: SalesOrder }) {
  const queryClient = useQueryClient();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canManage = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const canInvoice = hasRole('OWNER', 'ADMIN');

  const action = useMutation({
    mutationFn: async (path: string) => {
      await apiClient.post(path);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
      void queryClient.invalidateQueries({ queryKey: ['invoices'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  if (!canManage && !canInvoice) return <span className="text-xs text-text-muted">—</span>;

  return (
    <div className="flex justify-end gap-1">
      {canManage && order.status === 'DRAFT' && (
        <Button
          variant="secondary"
          size="sm"
          loading={action.isPending}
          onClick={() => action.mutate(`/api/v1/sales-orders/${order.id}/confirm`)}
        >
          Confirm
        </Button>
      )}
      {canManage && order.status === 'CONFIRMED' && (
        <Button
          variant="secondary"
          size="sm"
          loading={action.isPending}
          onClick={() => action.mutate(`/api/v1/sales-orders/${order.id}/allocate`)}
        >
          Allocate
        </Button>
      )}
      {canInvoice && (order.status === 'ALLOCATED' || order.status === 'SHIPPED') && (
        <Button
          variant="secondary"
          size="sm"
          loading={action.isPending}
          onClick={() => action.mutate(`/api/v1/invoices/from-sales-order/${order.id}`)}
        >
          Invoice
        </Button>
      )}
      {canManage && (order.status === 'DRAFT' || order.status === 'CONFIRMED') && (
        <Button
          variant="ghost"
          size="sm"
          loading={action.isPending}
          onClick={() => action.mutate(`/api/v1/sales-orders/${order.id}/cancel`)}
        >
          Cancel
        </Button>
      )}
    </div>
  );
}

export function SalesOrdersPage() {
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const [modalOpen, setModalOpen] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [peekOrderId, setPeekOrderId] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } =
    useListQuery<SalesOrder>(['sales-orders'], '/api/v1/sales-orders');

  const { data: peekOrder } = useQuery({
    queryKey: ['sales-orders', peekOrderId],
    queryFn: async () =>
      (await apiClient.get<SalesOrderDetail>(`/api/v1/sales-orders/${peekOrderId}`)).data,
    enabled: !!peekOrderId,
  });

  const filtered = useMemo(() => {
    if (!data) return [];
    if (!statusFilter) return data;
    return data.filter((o) => o.status === statusFilter);
  }, [data, statusFilter]);

  const orderPresets = [
    { id: 'all', label: 'All', filters: {} as Record<string, string> },
    { id: 'open', label: 'Open', filters: { status: 'CONFIRMED' } },
    { id: 'allocated', label: 'Allocated', filters: { status: 'ALLOCATED' } },
    { id: 'shipped', label: 'Shipped', filters: { status: 'SHIPPED' } },
  ];

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex shrink-0 items-center justify-between gap-4 border-b border-border/60 px-6 py-4">
        <div>
          <h1 className="text-2xl font-bold text-text">Sales Orders</h1>
          <p className="mt-1 text-sm text-text-muted">Outbound fulfillment</p>
        </div>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            New order
          </Button>
        )}
      </div>

      <div className="shrink-0 px-6 pt-4">
      <SavedFilterViews
        storageKey="sales-orders-filters"
        activeFilters={{ status: statusFilter }}
        onApply={(f) => setStatusFilter(f.status ?? '')}
        defaultPresets={orderPresets}
      />
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={filtered}
        refetch={refetch}
        emptyIcon={ShoppingCart}
        emptyTitle="No sales orders yet"
        emptyDescription={
          canCreate
            ? 'Create a sales order when a customer places an order.'
            : 'Sales orders will appear here as they come in.'
        }
        emptyAction={
          canCreate ? (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              Create sales order
            </Button>
          ) : undefined
        }
      >
        {(items) => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Number</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((so) => (
                <TableRow key={so.id} className="cursor-pointer" onClick={() => setPeekOrderId(so.id)}>
                  <TableCell mono>{so.number}</TableCell>
                  <TableCell>{so.customerName}</TableCell>
                  <TableCell>
                    <StatusBadge status={so.status} />
                  </TableCell>
                  <TableCell className="text-text-muted">
                    {new Date(so.createdAt).toLocaleDateString()}
                  </TableCell>
                  <TableCell align="right">
                    <div onClick={(e: MouseEvent) => e.stopPropagation()}>
                      <RowActions order={so} />
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </ListPageState>
      </div>

      <CreateOrderModal open={modalOpen} onClose={() => setModalOpen(false)} />

      <RightPeekDrawer
        open={!!peekOrderId}
        onClose={() => setPeekOrderId(null)}
        title={peekOrder?.number ?? 'Sales order'}
        description={peekOrder ? `${peekOrder.customerName} · ${peekOrder.status.replaceAll('_', ' ')}` : undefined}
      >
        {peekOrder ? (
          <div className="space-y-4">
            <ul className="divide-y divide-border rounded-lg border border-border">
              {peekOrder.lines.map((line) => (
                <li key={line.id} className="flex items-start justify-between gap-3 px-3 py-2.5 text-sm">
                  <div className="min-w-0">
                    <p className="truncate font-medium text-text">{line.name ?? 'Item'}</p>
                    <p className="font-mono text-xs text-text-muted">{line.sku ?? line.variantId.slice(0, 8)}</p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="tabular-nums text-text">
                      {line.qtyOrdered} × {formatCurrency(Number(line.unitPrice))}
                    </p>
                    {Number(line.qtyShipped) > 0 && (
                      <p className="text-xs text-text-muted">Shipped {line.qtyShipped}</p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
            <div className="flex justify-end" onClick={(e: MouseEvent) => e.stopPropagation()}>
              <RowActions order={{
                id: peekOrder.id,
                number: peekOrder.number,
                customerName: peekOrder.customerName,
                status: peekOrder.status,
                channel: 'DIRECT',
                createdAt: new Date().toISOString(),
              }} />
            </div>
          </div>
        ) : (
          <p className="text-sm text-text-muted">Loading…</p>
        )}
      </RightPeekDrawer>
    </div>
  );
}

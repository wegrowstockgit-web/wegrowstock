import { useMemo, useState, type MouseEvent, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState } from '@/components/layout/ListPageState';
import { useClientSort } from '@/hooks/useClientSort';
import { useSessionStore } from '@/stores/session';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  DRAFT_QUOTE: 'bg-surface-overlay text-text-muted',
  PENDING_REP_APPROVAL: 'bg-warning/10 text-warning',
  QUOTE_READY: 'bg-accent-muted text-accent',
  QUOTE_ACCEPTED: 'bg-accent-muted text-accent',
  UNALLOCATED: 'bg-warning/10 text-warning',
  CONFIRMED: 'bg-accent-muted text-accent',
  BACKORDERED: 'bg-warning/10 text-warning',
  PARTIALLY_ALLOCATED: 'bg-warning/10 text-warning',
  ALLOCATED: 'bg-accent-muted text-accent',
  PARTIALLY_SHIPPED: 'bg-warning/10 text-warning',
  SHIPPED: 'bg-success/10 text-success',
  CLOSED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
};

function defaultQuoteExpiry(): string {
  const d = new Date();
  d.setDate(d.getDate() + 14);
  return d.toISOString().slice(0, 10);
}

function AllocationHoldBadge({
  status,
  allocationPolicy,
}: {
  status: string;
  allocationPolicy?: string;
}) {
  const { t } = useTranslation();
  if (!['PARTIALLY_ALLOCATED', 'BACKORDERED', 'UNALLOCATED'].includes(status)) {
    return null;
  }
  const shipComplete = allocationPolicy === 'SHIP_COMPLETE';
  return (
    <p
      className="rounded-md border border-warning/40 bg-warning/10 px-2.5 py-1.5 text-xs text-warning"
      data-testid="allocation-hold-badge"
    >
      {shipComplete ? t('sales.heldByShipComplete') : t('sales.splitShipmentHold')}
    </p>
  );
}

function StatusBadge({ status }: { status: string }) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
        STATUS_STYLES[status] ?? 'bg-surface-overlay text-text-muted'
      )}
    >
      {t(`sales.statuses.${status}`, { defaultValue: status.replaceAll('_', ' ') })}
    </span>
  );
}

function SalesOrdersTable({
  items,
  onPeek,
  renderActions,
}: {
  items: SalesOrder[];
  onPeek: (id: string) => void;
  renderActions: (order: SalesOrder) => ReactNode;
}) {
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      number: (o) => o.number,
      customer: (o) => o.customerName,
      status: (o) => o.status,
      created: (o) => o.createdAt,
    },
    { key: 'created', dir: 'desc' },
  );
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="number" sort={sort} onSort={toggle}>
            Number
          </TableHead>
          <TableHead sortable sortKey="customer" sort={sort} onSort={toggle}>
            Customer
          </TableHead>
          <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
            Status
          </TableHead>
          <TableHead sortable sortKey="created" sort={sort} onSort={toggle}>
            Created
          </TableHead>
          <TableHead align="right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((so) => (
          <TableRow
            key={so.id}
            className={cn(
              'cursor-pointer',
              so.status === 'PENDING_REP_APPROVAL' && 'bg-warning/10',
            )}
            data-rfq={so.status === 'PENDING_REP_APPROVAL' ? 'true' : undefined}
            onClick={() => onPeek(so.id)}
          >
            <TableCell mono>{so.number}</TableCell>
            <TableCell>{so.customerName}</TableCell>
            <TableCell>
              <StatusBadge status={so.status} />
            </TableCell>
            <TableCell className="text-text-muted">
              {new Date(so.createdAt).toLocaleDateString()}
            </TableCell>
            <TableCell align="right">
              <div onClick={(e: MouseEvent) => e.stopPropagation()}>{renderActions(so)}</div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
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
      {canManage &&
        (order.status === 'CONFIRMED' ||
          order.status === 'BACKORDERED' ||
          order.status === 'UNALLOCATED' ||
          order.status === 'PARTIALLY_ALLOCATED') && (
        <Button
          variant="secondary"
          size="sm"
          loading={action.isPending}
          onClick={() => action.mutate(`/api/v1/sales-orders/${order.id}/allocate`)}
        >
          Allocate
        </Button>
      )}
      {canInvoice &&
        (order.status === 'ALLOCATED' ||
          order.status === 'PARTIALLY_SHIPPED' ||
          order.status === 'SHIPPED') &&
        (order.billingStatus === 'INVOICED' ? (
          <span className="text-xs font-medium text-text-muted">Invoiced</span>
        ) : (
          <Button
            variant="secondary"
            size="sm"
            loading={action.isPending}
            onClick={() => action.mutate(`/api/v1/invoices/from-sales-order/${order.id}`)}
          >
            {order.billingStatus === 'PARTIAL' ? 'Invoice remaining' : 'Invoice'}
          </Button>
        ))}
      {canManage &&
        (order.status === 'DRAFT' ||
          order.status === 'CONFIRMED' ||
          order.status === 'PENDING_REP_APPROVAL' ||
          order.status === 'QUOTE_READY') && (
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
  const { t } = useTranslation();
  const location = useLocation();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const [modalOpen, setModalOpen] = useState(false);
  const [statusFilter, setStatusFilter] = useState(() =>
    location.pathname.startsWith('/sales/orders') ? 'PENDING_REP_APPROVAL' : '',
  );
  const [peekOrderId, setPeekOrderId] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['sales-orders'],
    queryFn: async () => (await apiClient.get<SalesOrder[]>('/api/v1/sales-orders')).data,
    refetchInterval: 3_000,
  });

  const { data: peekOrder } = useQuery({
    queryKey: ['sales-orders', peekOrderId],
    queryFn: async () =>
      (await apiClient.get<SalesOrderDetail>(`/api/v1/sales-orders/${peekOrderId}`)).data,
    enabled: !!peekOrderId,
    refetchInterval: peekOrderId ? 3_000 : false,
  });

  const filtered = useMemo(() => {
    if (!data) return [];
    if (!statusFilter) return data;
    return data.filter((o) => o.status === statusFilter);
  }, [data, statusFilter]);

  const orderPresets = [
    { id: 'all', label: t('sales.filterAll'), filters: {} as Record<string, string> },
    { id: 'rfq', label: t('sales.rfqInbox'), filters: { status: 'PENDING_REP_APPROVAL' } },
    { id: 'open', label: t('status.open'), filters: { status: 'CONFIRMED' } },
    { id: 'allocated', label: t('status.allocated'), filters: { status: 'ALLOCATED' } },
    { id: 'shipped', label: t('status.shipped'), filters: { status: 'SHIPPED' } },
  ];

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-col">
      <div
        className="flex shrink-0 items-center justify-between gap-4 border-b border-border/60 px-6 py-4"
        data-tour="tour-so-allocation"
      >
        <div>
          <h1 className="text-2xl font-bold text-text">{t('sales.title')}</h1>
          <p className="mt-1 text-sm text-text-muted">{t('sales.subtitle')}</p>
        </div>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            {t('sales.newOrder')}
          </Button>
        )}
      </div>

      <div className="shrink-0 px-6 pt-4">
        <DataListToolbar>
          <SavedFilterViews
            className="mb-0"
            storageKey="sales-orders-filters"
            activeFilters={{ status: statusFilter }}
            onApply={(f) => setStatusFilter(f.status ?? '')}
            defaultPresets={orderPresets}
          />
        </DataListToolbar>
      </div>

      <div className="min-h-0 min-w-0 flex-1 overflow-auto" data-list-scrollport="true">
      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={filtered}
        refetch={refetch}
        emptyIcon={ShoppingCart}
        emptyTitle={t('sales.emptyTitle')}
        emptyDescription={
          canCreate ? t('sales.emptyDescriptionCreate') : t('sales.emptyDescription')
        }
        emptyAction={
          canCreate ? (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              {t('sales.createOrder')}
            </Button>
          ) : undefined
        }
      >
        {(items) => (
          <SalesOrdersTable
            items={items}
            onPeek={setPeekOrderId}
            renderActions={(so) => <RowActions order={so} />}
          />
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
            <AllocationHoldBadge
              status={peekOrder.status}
              allocationPolicy={peekOrder.allocationPolicy}
            />
            {peekOrder.status === 'PENDING_REP_APPROVAL' || peekOrder.status === 'QUOTE_READY' ? (
              <QuoteNegotiationForm order={peekOrder} onSent={() => setPeekOrderId(null)} />
            ) : (
              <>
                <ul className="divide-y divide-border rounded-lg border border-border">
                  {peekOrder.lines.map((line) => (
                    <li key={line.id} className="flex items-start justify-between gap-3 px-3 py-2.5 text-sm">
                      <div className="min-w-0">
                        <p className="truncate font-medium text-text">{line.name ?? 'Item'}</p>
                        <p className="font-mono text-xs text-text-muted">{line.sku ?? line.variantId.slice(0, 8)}</p>
                        {Number(line.qtyBackordered ?? 0) > 0 && (
                          <p className="text-xs text-warning">Backordered {line.qtyBackordered}</p>
                        )}
                      </div>
                      <div className="shrink-0 text-right">
                        <p className="tabular-nums text-text">
                          {line.qtyOrdered} × {formatCurrency(Number(line.unitPrice))}
                        </p>
                        {Number(line.qtyAllocated ?? 0) > 0 && (
                          <p className="text-xs text-text-muted">Allocated {line.qtyAllocated}</p>
                        )}
                        {Number(line.qtyShipped) > 0 && (
                          <p className="text-xs text-text-muted">Shipped {line.qtyShipped}</p>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
                <div className="flex justify-end" onClick={(e: MouseEvent) => e.stopPropagation()}>
                  <RowActions
                    order={{
                      id: peekOrder.id,
                      number: peekOrder.number,
                      customerName: peekOrder.customerName,
                      status: peekOrder.status,
                      channel: 'DIRECT',
                      createdAt: new Date().toISOString(),
                      allocationPolicy: peekOrder.allocationPolicy,
                    }}
                  />
                </div>
              </>
            )}
          </div>
        ) : (
          <p className="text-sm text-text-muted">Loading…</p>
        )}
      </RightPeekDrawer>
    </div>
  );
}

function QuoteNegotiationForm({
  order,
  onSent,
}: {
  order: SalesOrderDetail;
  onSent: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [prices, setPrices] = useState<Record<string, string>>(() =>
    Object.fromEntries(order.lines.map((line) => [line.id, String(line.unitPrice)])),
  );
  const [discount, setDiscount] = useState(String(order.manualDiscountTotal ?? 0));
  const [expires, setExpires] = useState(
    order.quoteExpiresAt ? order.quoteExpiresAt.slice(0, 10) : defaultQuoteExpiry(),
  );
  const [notes, setNotes] = useState(order.quoteNotes ?? '');
  const [error, setError] = useState('');

  const sendQuote = useMutation({
    mutationFn: async () => {
      const expiryDate = new Date(`${expires}T23:59:59.000Z`);
      await apiClient.post(`/api/v1/sales-orders/${order.id}/quote`, {
        linePrices: order.lines.map((line) => ({
          lineId: line.id,
          unitPrice: Number(prices[line.id] || line.unitPrice),
        })),
        manualDiscountTotal: Number(discount || 0),
        quoteExpiresAt: expiryDate.toISOString(),
        quoteNotes: notes || undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
      onSent();
    },
    onError: () => setError(t('sales.quoteSendFailed')),
  });

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        setError('');
        sendQuote.mutate();
      }}
    >
      <p className="text-sm text-text-muted">
        {t('sales.quoteHelp')}
      </p>
      <ul className="divide-y divide-border rounded-lg border border-border">
        {order.lines.map((line) => (
          <li key={line.id} className="space-y-2 px-3 py-2.5">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-text">{line.name ?? 'Item'}</p>
                <p className="font-mono text-xs text-text-muted">
                  {line.sku ?? line.variantId.slice(0, 8)} · qty {line.qtyOrdered}
                </p>
              </div>
            </div>
            <Input
              label={t('sales.unitPrice')}
              type="number"
              min="0"
              step="0.01"
              value={prices[line.id] ?? ''}
              onChange={(e) => setPrices((prev) => ({ ...prev, [line.id]: e.target.value }))}
            />
          </li>
        ))}
      </ul>
      <Input
        label={t('sales.flatDiscount')}
        type="number"
        min="0"
        step="0.01"
        value={discount}
        onChange={(e) => setDiscount(e.target.value)}
      />
      <Input
        label={t('sales.quoteExpires')}
        type="date"
        value={expires}
        onChange={(e) => setExpires(e.target.value)}
      />
      <div className="flex flex-col gap-1.5">
        <label htmlFor="rep-quote-notes" className="text-sm font-medium text-text">
          {t('sales.notesToCustomer')}
        </label>
        <textarea
          id="rep-quote-notes"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          className="w-full rounded-md border border-border bg-surface-raised px-3 py-2 text-sm text-text placeholder:text-text-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
        />
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      <Button type="submit" className="w-full" loading={sendQuote.isPending}>
        {t('sales.sendQuote')}
      </Button>
    </form>
  );
}

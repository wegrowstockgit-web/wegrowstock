import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, ClipboardList, FileUp, Plus, Trash2 } from 'lucide-react';
import { stashApInvoiceFile } from '@/features/purchasing/apInvoiceFileStore';
import { apiClient } from '@/api/client';
import type {
  PaginatedResponse,
  ProductVariant,
  PurchaseOrder,
  PurchaseOrderDetail,
  Supplier,
  TenantLocation,
} from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { cn, formatCurrency, formatMediumDate } from '@/lib/utils';
import { selectColumnVisible, useGridColumnStore } from '@/stores/gridColumnStore';
import type { ColumnVisibilityItem } from '@/components/ui/ColumnVisibilityMenu';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { DebouncedSearchInput } from '@/components/ui/DebouncedSearchInput';
import { Pagination } from '@/components/ui/Pagination';
import { TableDensityScope } from '@/hooks/useDensity';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { useClientSort } from '@/hooks/useClientSort';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { useSessionStore } from '@/stores/session';
import { unwrapPageItems } from '@/api/page';
import { listPurchaseOrders } from '@/api/operational';

const RECEIVABLE = new Set(['SUBMITTED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED']);
const PO_GRID_ID = 'purchase-orders';
const PO_COLUMN_ITEMS: ColumnVisibilityItem[] = [
  { id: 'number', label: 'Number' },
  { id: 'supplier', label: 'Supplier' },
  { id: 'status', label: 'Status' },
  { id: 'createdAt', label: 'Created Date' },
  { id: 'expected', label: 'Expected' },
  { id: 'total', label: 'Total' },
  { id: 'progress', label: 'Progress' },
  { id: 'vendorRef', label: 'Vendor Ref' },
];
const PO_OPS_COLUMN_IDS = ['number', 'supplier', 'status', 'expected', 'progress'] as const;

function etaOf(po: PurchaseOrder): string {
  return po.expectedDeliveryDate ?? po.expectedAt ?? '';
}

function PurchaseOrdersTable({
  items,
  onPeek,
}: {
  items: PurchaseOrder[];
  onPeek: (id: string) => void;
}) {
  const ensureColumns = useGridColumnStore((s) => s.ensureColumns);
  const showNumber = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'number'));
  const showSupplier = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'supplier'));
  const showStatus = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'status'));
  const showCreated = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'createdAt'));
  const showExpected = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'expected'));
  const showTotal = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'total'));
  const showProgress = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'progress'));
  const showVendorRef = useGridColumnStore((s) => selectColumnVisible(s, PO_GRID_ID, 'vendorRef'));

  useEffect(() => {
    ensureColumns(
      PO_GRID_ID,
      PO_COLUMN_ITEMS.map((c) => c.id),
      {
        columnOrder: PO_COLUMN_ITEMS.map((c) => c.id),
        columnVisibility: { vendorRef: false },
      },
    );
  }, [ensureColumns]);

  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      number: (po) => po.number,
      supplier: (po) => po.supplierName,
      status: (po) => po.status,
      createdAt: (po) => po.createdAt ?? '',
      expected: (po) => etaOf(po),
      total: (po) => po.totalAmount ?? 0,
      progress: (po) => po.totalQtyReceived ?? 0,
      vendorRef: (po) => po.vendorReference ?? '',
    },
    { key: 'number', dir: 'desc' },
  );
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {showNumber ? (
            <TableHead sortable sortKey="number" sort={sort} onSort={toggle}>
              Number
            </TableHead>
          ) : null}
          {showSupplier ? (
            <TableHead sortable sortKey="supplier" sort={sort} onSort={toggle}>
              Supplier
            </TableHead>
          ) : null}
          {showStatus ? (
            <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
              Status
            </TableHead>
          ) : null}
          {showCreated ? (
            <TableHead sortable sortKey="createdAt" sort={sort} onSort={toggle}>
              Created Date
            </TableHead>
          ) : null}
          {showExpected ? (
            <TableHead sortable sortKey="expected" sort={sort} onSort={toggle}>
              Expected
            </TableHead>
          ) : null}
          {showTotal ? (
            <TableHead sortable sortKey="total" sort={sort} onSort={toggle} align="right">
              Total
            </TableHead>
          ) : null}
          {showProgress ? (
            <TableHead sortable sortKey="progress" sort={sort} onSort={toggle}>
              Progress
            </TableHead>
          ) : null}
          {showVendorRef ? (
            <TableHead sortable sortKey="vendorRef" sort={sort} onSort={toggle}>
              Vendor Ref
            </TableHead>
          ) : null}
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((po) => (
          <TableRow key={po.id} className="cursor-pointer" onClick={() => onPeek(po.id)}>
            {showNumber ? <TableCell mono>{po.number}</TableCell> : null}
            {showSupplier ? <TableCell>{po.supplierName}</TableCell> : null}
            {showStatus ? (
              <TableCell>
                <span
                  className={cn(
                    'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                    STATUS_STYLES[po.status] ?? 'bg-surface-overlay text-text-muted',
                  )}
                >
                  {po.status.replaceAll('_', ' ')}
                </span>
              </TableCell>
            ) : null}
            {showCreated ? (
              <TableCell className="text-text-muted">{formatMediumDate(po.createdAt)}</TableCell>
            ) : null}
            {showExpected ? (
              <TableCell className="text-text-muted">{formatMediumDate(etaOf(po) || null)}</TableCell>
            ) : null}
            {showTotal ? (
              <TableCell align="right" mono>
                {po.totalAmount != null ? formatCurrency(Number(po.totalAmount)) : '—'}
              </TableCell>
            ) : null}
            {showProgress ? (
              <TableCell mono data-testid={`po-progress-${po.id}`}>
                {po.totalQtyReceived ?? 0} / {po.totalQtyOrdered ?? 0}
              </TableCell>
            ) : null}
            {showVendorRef ? (
              <TableCell className="text-text-muted">{po.vendorReference || '—'}</TableCell>
            ) : null}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  SUBMITTED: 'bg-accent-muted text-accent',
  PARTIALLY_RECEIVED: 'bg-warning/10 text-warning',
  RECEIVED: 'bg-success/10 text-success',
  CLOSED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
};

interface DraftLine {
  variantId: string;
  qtyOrdered: string;
  unitCost: string;
}

function CreatePoModal({
  open,
  onClose,
  initialSku,
  initialSupplierId,
}: {
  open: boolean;
  onClose: () => void;
  initialSku?: string;
  initialSupplierId?: string;
}) {
  const queryClient = useQueryClient();
  const [supplierId, setSupplierId] = useState(initialSupplierId ?? '');
  const [destinationLocationId, setDestinationLocationId] = useState('');
  const [freightAmount, setFreightAmount] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([{ variantId: '', qtyOrdered: '1', unitCost: '' }]);
  const [error, setError] = useState('');

  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers', 'lookup'],
    queryFn: async () =>
      unwrapPageItems<Supplier>(
        (await apiClient.get('/api/v1/suppliers', { params: { page: 1, size: 100 } })).data,
      ),
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

  useEffect(() => {
    if (!open) return;
    if (initialSupplierId) {
      setSupplierId(initialSupplierId);
    }
    if (initialSku) {
      const match = variants.find((variant) => variant.sku === initialSku);
      if (match) {
        setLines((prev) => {
          if (prev.some((line) => line.variantId === match.id)) return prev;
          return [{ variantId: match.id, qtyOrdered: '1', unitCost: '' }, ...prev.filter((line) => line.variantId)];
        });
      }
    }
  }, [open, initialSku, initialSupplierId, variants]);

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/purchase-orders', {
        supplierId,
        number: `PO-${Date.now()}`,
        destinationLocationId: destinationLocationId || undefined,
        freightAmount: freightAmount ? Number(freightAmount) : undefined,
        lines: lines
          .filter((l) => l.variantId && Number(l.qtyOrdered) > 0)
          .map((l) => ({
            variantId: l.variantId,
            qtyOrdered: Number(l.qtyOrdered),
            unitCost: l.unitCost ? Number(l.unitCost) : undefined,
          })),
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
      setSupplierId('');
      setDestinationLocationId('');
      setFreightAmount('');
      setLines([{ variantId: '', qtyOrdered: '1', unitCost: '' }]);
      onClose();
    },
    onError: () => setError('Could not create the purchase order. Check the fields and try again.'),
  });

  const updateLine = (index: number, patch: Partial<DraftLine>) => {
    setLines((prev) => prev.map((l, i) => (i === index ? { ...l, ...patch } : l)));
  };

  const validLines = lines.filter((l) => l.variantId && Number(l.qtyOrdered) > 0);

  return (
    <Modal open={open} onClose={onClose} title="New purchase order" description="PO number is assigned automatically">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Select
          label="Supplier"
          value={supplierId}
          onChange={(e) => setSupplierId(e.target.value)}
          required
        >
          <option value="" disabled>
            Select a supplier…
          </option>
          {suppliers.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>

        <Select
          label="Destination warehouse"
          value={destinationLocationId}
          onChange={(e) => setDestinationLocationId(e.target.value)}
        >
          <option value="">Default receiving location</option>
          {warehouses.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </Select>

        <Input
          label="Freight amount"
          type="number"
          min="0"
          step="0.01"
          value={freightAmount}
          onChange={(e) => setFreightAmount(e.target.value)}
          placeholder="0.00"
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
                  aria-label="Unit cost"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="Cost"
                  value={line.unitCost}
                  onChange={(e) => updateLine(index, { unitCost: e.target.value })}
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
            onClick={() => setLines((prev) => [...prev, { variantId: '', qtyOrdered: '1', unitCost: '' }])}
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
          <Button type="submit" loading={mutation.isPending} disabled={!supplierId || validLines.length === 0}>
            Create PO
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function ReceivePoModal({
  open,
  poId,
  onClose,
}: {
  open: boolean;
  poId: string | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [locationId, setLocationId] = useState('');
  const [surchargeOpen, setSurchargeOpen] = useState(false);
  const [landedCostSurcharge, setLandedCostSurcharge] = useState('');
  const [qtyByLine, setQtyByLine] = useState<Record<string, string>>({});
  const [error, setError] = useState('');

  const { data: detail, isLoading } = useQuery({
    queryKey: ['purchase-orders', poId],
    queryFn: async () =>
      (await apiClient.get<PurchaseOrderDetail>(`/api/v1/purchase-orders/${poId}`)).data,
    enabled: open && !!poId,
  });

  const { data: warehouses = [] } = useQuery({
    queryKey: ['locations', 'warehouse'],
    queryFn: async () =>
      (await apiClient.get<TenantLocation[]>('/api/v1/locations', { params: { type: 'WAREHOUSE' } })).data,
    enabled: open,
  });

  useEffect(() => {
    if (!detail) return;
    setLocationId(detail.destinationLocationId ?? '');
    const next: Record<string, string> = {};
    for (const line of detail.lines) {
      const remaining = Math.max(0, Number(line.qtyOrdered) - Number(line.qtyReceived));
      next[line.id] = remaining > 0 ? String(remaining) : '';
    }
    setQtyByLine(next);
    setLandedCostSurcharge('');
    setSurchargeOpen(false);
    setError('');
  }, [detail]);

  const receiveMutation = useMutation({
    mutationFn: async () => {
      if (!detail || !locationId) throw new Error('Location required');
      const lines = detail.lines
        .map((line) => ({
          lineId: line.id,
          quantity: Number(qtyByLine[line.id] || 0),
        }))
        .filter((l) => l.quantity > 0);
      if (lines.length === 0) throw new Error('Enter a quantity to receive');
      const surcharge = landedCostSurcharge.trim() ? Number(landedCostSurcharge) : undefined;
      await apiClient.post('/api/v1/purchasing/receive', {
        purchaseOrderId: detail.id,
        locationId,
        landedCostSurcharge: surcharge,
        lines,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
      onClose();
    },
    onError: () => setError('Could not receive this purchase order. Check quantities and try again.'),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Receive purchase order"
      description={detail ? `${detail.number} · ${detail.supplierName}` : 'Load lines and put stock away'}
    >
      {isLoading || !detail ? (
        <p className="text-sm text-text-muted">Loading lines…</p>
      ) : (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            setError('');
            receiveMutation.mutate();
          }}
        >
          <Select
            label="Receive into location"
            value={locationId}
            onChange={(e) => setLocationId(e.target.value)}
            required
          >
            <option value="" disabled>
              Select location…
            </option>
            {warehouses.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </Select>

          <div className="space-y-2">
            <p className="text-sm font-medium text-text">Lines</p>
            {detail.lines.map((line) => {
              const remaining = Math.max(0, Number(line.qtyOrdered) - Number(line.qtyReceived));
              return (
                <div
                  key={line.id}
                  className="flex flex-wrap items-end gap-3 rounded-lg border border-border/70 p-3"
                >
                  <div className="min-w-0 flex-1 text-sm">
                    <p className="font-mono text-text">{line.variantId.slice(0, 8)}…</p>
                    <p className="text-text-muted">
                      Ordered {line.qtyOrdered} · Received {line.qtyReceived} · Remaining {remaining}
                    </p>
                  </div>
                  <Input
                    label="Qty"
                    type="number"
                    min={0}
                    step="any"
                    className="w-28"
                    value={qtyByLine[line.id] ?? ''}
                    onChange={(e) =>
                      setQtyByLine((prev) => ({ ...prev, [line.id]: e.target.value }))
                    }
                    disabled={remaining <= 0}
                  />
                </div>
              );
            })}
          </div>

          <div className="rounded-lg border border-border/70">
            <button
              type="button"
              className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-sm font-medium text-text hover:bg-surface-overlay"
              aria-expanded={surchargeOpen}
              onClick={() => setSurchargeOpen((o) => !o)}
            >
              Add Freight/Customs Surcharges
              <ChevronDown
                className={cn('h-4 w-4 text-text-muted transition-transform', surchargeOpen && 'rotate-180')}
              />
            </button>
            {surchargeOpen && (
              <div className="border-t border-border/70 px-3 py-3">
                <Input
                  label="Landed cost surcharge"
                  type="number"
                  min={0}
                  step="0.01"
                  value={landedCostSurcharge}
                  onChange={(e) => setLandedCostSurcharge(e.target.value)}
                  placeholder="0.00"
                  data-testid="landed-cost-surcharge"
                />
                <p className="mt-1.5 text-xs text-text-muted">
                  Distributed across received lines by value and folded into unit cost for COGS.
                </p>
              </div>
            )}
          </div>

          {error && <p className="text-sm text-danger">{error}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={receiveMutation.isPending} disabled={!locationId}>
              Receive
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}

function ApIngestionPanel() {
  const navigate = useNavigate();
  const [dragOver, setDragOver] = useState(false);

  function openWorkspace(file?: File | null) {
    if (file) stashApInvoiceFile(file);
    navigate('/purchasing/ap-ingestion');
  }

  return (
    <Card className="mt-8">
      <CardHeader
        title="AP invoice reconciliation"
        description="Open the split-screen workspace to preview the vendor bill and run a 3-way match"
      />
      <div className="space-y-4 p-4">
        <div className="space-y-3" data-testid="document-ai-upload">
          <div
            className={cn(
              'flex min-h-40 cursor-pointer flex-col items-center justify-center gap-3 rounded-lg border border-dashed px-4 py-8 text-center transition-colors',
              dragOver ? 'border-accent bg-accent/5' : 'border-border bg-surface-overlay/40',
            )}
            data-testid="document-ai-dropzone"
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              openWorkspace(e.dataTransfer.files?.[0] ?? null);
            }}
            onClick={() => document.getElementById('ap-invoice-file-input')?.click()}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                document.getElementById('ap-invoice-file-input')?.click();
              }
            }}
            role="button"
            tabIndex={0}
            aria-label="Drop invoice document or browse"
          >
            <FileUp className="h-8 w-8 text-text-muted" aria-hidden />
            <div className="text-sm text-text">
              Drop a PDF or image here, or <span className="text-accent">browse</span>
            </div>
            <p className="text-xs text-text-muted">Opens the AP Document Workspace — no JSON paste</p>
            <input
              id="ap-invoice-file-input"
              type="file"
              accept=".pdf,.txt,.csv,image/*"
              className="hidden"
              onChange={(e) => openWorkspace(e.target.files?.[0] ?? null)}
            />
          </div>
          <Button
            className="min-h-11 touch-target"
            data-testid="document-ai-upload-btn"
            onClick={() => navigate('/purchasing/ap-ingestion')}
          >
            Open AP workspace
          </Button>
        </div>
      </div>
    </Card>
  );
}

export function PurchaseOrdersPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const meshPartnerSku = searchParams.get('meshPartnerSku') ?? undefined;
  const meshSupplierId = searchParams.get('supplierId') ?? undefined;
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const canReceive = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER');
  const [modalOpen, setModalOpen] = useState(Boolean(meshPartnerSku || meshSupplierId));
  const [peekPoId, setPeekPoId] = useState<string | null>(null);
  const [receivePoId, setReceivePoId] = useState<string | null>(null);

  const table = useServerTableQuery<PurchaseOrder>({
    queryKey: 'purchase-orders',
    path: '/api/v1/purchase-orders',
    defaultSort: 'createdAt,desc',
    fetcher: listPurchaseOrders,
  });
  const { items, isLoading, isError, error, refetch, search } = table;
  const peekPo = items.find((po) => po.id === peekPoId) ?? null;
  const ensureColumns = useGridColumnStore((s) => s.ensureColumns);

  useEffect(() => {
    ensureColumns(
      PO_GRID_ID,
      PO_COLUMN_ITEMS.map((c) => c.id),
      {
        columnOrder: PO_COLUMN_ITEMS.map((c) => c.id),
        columnVisibility: { vendorRef: false },
      },
    );
  }, [ensureColumns]);

  return (
    <TableDensityScope gridId="purchase-orders">
    <div className="flex h-full min-h-0 min-w-0 flex-col">
      <div className="flex shrink-0 items-center justify-between gap-4 border-b border-border/60 px-6 py-4">
        <div>
          <h1 className="text-2xl font-bold text-text">Purchase Orders</h1>
          <p className="mt-1 text-sm text-text-muted">Inbound supply chain</p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2" data-tour="tour-po-receive-cta">
          {canReceive && (
            <Button
              variant="secondary"
              onClick={() => navigate('/inbound/receive?po=PO-2026-00001')}
              data-testid="tour-po-floor-receive"
            >
              Floor receive
            </Button>
          )}
          {canCreate && (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              New PO
            </Button>
          )}
        </div>
      </div>

      <div className="shrink-0 px-6 pt-4">
        <DataListToolbar
          gridId={PO_GRID_ID}
          columnItems={PO_COLUMN_ITEMS}
          opsOnlyColumnIds={PO_OPS_COLUMN_IDS}
        >
          <DebouncedSearchInput
            value={search}
            onDebouncedChange={table.setSearch}
            placeholder="Search orders or suppliers…"
          />
        </DataListToolbar>
      </div>

      <div
        className="min-h-0 min-w-0 flex-1 overflow-auto"
        data-list-scrollport="true"
        data-tour="tour-po-grid"
      >
        <ListPageState
          isLoading={isLoading && items.length === 0}
          isError={isError}
          error={error}
          data={items}
          refetch={refetch}
          emptyIcon={ClipboardList}
          emptyTitle={search ? 'No matching purchase orders' : 'No purchase orders yet'}
          emptyDescription={
            search
              ? 'Try a different order number, status, or supplier name.'
              : canCreate
              ? 'Create a purchase order to start receiving inventory.'
              : 'Purchase orders will appear here once created by a manager.'
          }
          emptyAction={
            canCreate ? (
              <Button onClick={() => setModalOpen(true)}>
                <Plus className="h-4 w-4" />
                Create purchase order
              </Button>
            ) : undefined
          }
        >
          {(items) => (
            <div className="w-full px-6 pb-6">
              <PurchaseOrdersTable items={items} onPeek={setPeekPoId} />
              <Pagination
                page={table.page}
                totalPages={table.totalPages}
                totalElements={table.totalElements}
                size={table.size}
                onPageChange={table.setPage}
                onSizeChange={table.setSize}
              />
            </div>
          )}
        </ListPageState>

        {canCreate && (
          <div className="border-t border-border/60 px-6 pb-6">
            <ApIngestionPanel />
          </div>
        )}
      </div>

      <CreatePoModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        initialSku={meshPartnerSku}
        initialSupplierId={meshSupplierId}
      />
      <ReceivePoModal
        open={!!receivePoId}
        poId={receivePoId}
        onClose={() => setReceivePoId(null)}
      />

      <RightPeekDrawer
        open={!!peekPoId}
        onClose={() => setPeekPoId(null)}
        title={peekPo?.number ?? 'Purchase order'}
        description={
          peekPo
            ? `${peekPo.supplierName} · ${peekPo.status.replaceAll('_', ' ')}`
            : undefined
        }
      >
        {peekPo ? (
          <div className="space-y-4">
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Supplier</dt>
                <dd className="font-medium text-text">{peekPo.supplierName}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Status</dt>
                <dd>
                  <span
                    className={cn(
                      'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                      STATUS_STYLES[peekPo.status] ?? 'bg-surface-overlay text-text-muted'
                    )}
                  >
                    {peekPo.status.replaceAll('_', ' ')}
                  </span>
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Created</dt>
                <dd>{formatMediumDate(peekPo.createdAt)}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Expected</dt>
                <dd>{formatMediumDate(peekPo.expectedDeliveryDate ?? peekPo.expectedAt)}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Total</dt>
                <dd className="font-mono tabular-nums">
                  {peekPo.totalAmount != null ? formatCurrency(Number(peekPo.totalAmount)) : '—'}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Progress</dt>
                <dd className="font-mono tabular-nums">
                  {peekPo.totalQtyReceived ?? 0} / {peekPo.totalQtyOrdered ?? 0}
                </dd>
              </div>
              {peekPo.vendorReference ? (
                <div className="flex justify-between gap-4">
                  <dt className="text-text-muted">Vendor Ref</dt>
                  <dd>{peekPo.vendorReference}</dd>
                </div>
              ) : null}
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Freight</dt>
                <dd className="font-mono tabular-nums">
                  {peekPo.freightAmount != null ? formatCurrency(Number(peekPo.freightAmount)) : '—'}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Duties</dt>
                <dd className="font-mono tabular-nums">
                  {peekPo.dutiesAmount != null ? peekPo.dutiesAmount.toFixed(2) : '—'}
                </dd>
              </div>
            </dl>
            <Button
              className="w-full"
              data-testid="open-po-workspace"
              onClick={() => {
                setPeekPoId(null);
                navigate(`/purchasing/orders/${peekPo.id}`);
              }}
            >
              Open Workspace
            </Button>
            {canReceive && RECEIVABLE.has(peekPo.status) && (
              <Button
                className="w-full"
                variant="secondary"
                data-testid="open-receive-po"
                onClick={() => {
                  setReceivePoId(peekPo.id);
                  setPeekPoId(null);
                }}
              >
                Receive stock
              </Button>
            )}
          </div>
        ) : (
          <p className="text-sm text-text-muted">Loading…</p>
        )}
      </RightPeekDrawer>
    </div>
    </TableDensityScope>
  );
}

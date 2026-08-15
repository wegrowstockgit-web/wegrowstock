import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, ClipboardList, FileUp, Plus, Trash2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type {
  PaginatedResponse,
  ProductVariant,
  PurchaseOrder,
  PurchaseOrderDetail,
  Supplier,
  SupplierInvoiceIngestion,
  TenantLocation,
} from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { cn } from '@/lib/utils';
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
import { ListPageState, useListQuery } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { useClientSort } from '@/hooks/useClientSort';
import { useSessionStore } from '@/stores/session';

const RECEIVABLE = new Set(['SUBMITTED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED']);

function PurchaseOrdersTable({
  items,
  onPeek,
}: {
  items: PurchaseOrder[];
  onPeek: (id: string) => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      number: (po) => po.number,
      supplier: (po) => po.supplierName,
      status: (po) => po.status,
      expected: (po) => po.expectedAt ?? '',
      freight: (po) => po.freightAmount ?? 0,
    },
    { key: 'number', dir: 'desc' },
  );
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="number" sort={sort} onSort={toggle}>
            Number
          </TableHead>
          <TableHead sortable sortKey="supplier" sort={sort} onSort={toggle}>
            Supplier
          </TableHead>
          <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
            Status
          </TableHead>
          <TableHead sortable sortKey="expected" sort={sort} onSort={toggle}>
            Expected
          </TableHead>
          <TableHead sortable sortKey="freight" sort={sort} onSort={toggle} align="right">
            Freight
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((po) => (
          <TableRow key={po.id} className="cursor-pointer" onClick={() => onPeek(po.id)}>
            <TableCell mono>{po.number}</TableCell>
            <TableCell>{po.supplierName}</TableCell>
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
            <TableCell className="text-text-muted">
              {po.expectedAt ? new Date(po.expectedAt).toLocaleDateString() : '—'}
            </TableCell>
            <TableCell align="right" mono>
              {po.freightAmount != null ? po.freightAmount.toFixed(2) : '—'}
            </TableCell>
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

function CreatePoModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [supplierId, setSupplierId] = useState('');
  const [destinationLocationId, setDestinationLocationId] = useState('');
  const [freightAmount, setFreightAmount] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([{ variantId: '', qtyOrdered: '1', unitCost: '' }]);
  const [error, setError] = useState('');

  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers'],
    queryFn: async () => (await apiClient.get<Supplier[]>('/api/v1/suppliers')).data,
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

interface ApDocIngestion {
  id: string;
  fileStorageKey: string;
  ingestionStatus: string;
  parsedMetadata?: Record<string, unknown>;
  matchedPurchaseOrderId?: string | null;
  createdAt: string;
}

function ApIngestionPanel({ purchaseOrders }: { purchaseOrders: PurchaseOrder[] }) {
  const queryClient = useQueryClient();
  const [poId, setPoId] = useState('');
  const [documentUrl, setDocumentUrl] = useState('');
  const [jsonPayload, setJsonPayload] = useState(
    '{\n  "lines": [\n    { "sku": "WIDGET-S", "qty": 100, "unitCost": 5.00 }\n  ]\n}'
  );
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);

  const { data: ingestions = [] } = useQuery({
    queryKey: ['ap', 'ingestions'],
    queryFn: async () =>
      (await apiClient.get<SupplierInvoiceIngestion[]>('/api/v1/ap/ingestions')).data,
    retry: false,
  });

  const { data: docIngestions = [] } = useQuery({
    queryKey: ['ap', 'doc-ingestions'],
    queryFn: async () =>
      (await apiClient.get<ApDocIngestion[]>('/api/v1/ap-ingestions')).data,
    refetchInterval: 3000,
    retry: false,
  });

  const submitMutation = useMutation({
    mutationFn: async () => {
      const extractedData = JSON.parse(jsonPayload) as Record<string, unknown>;
      await apiClient.post('/api/v1/ap/ingestions', {
        purchaseOrderId: poId,
        documentUrl: documentUrl || undefined,
        extractedData,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['ap', 'ingestions'] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
    },
  });

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append('file', file);
      await apiClient.post('/api/v1/ap-ingestions/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    },
    onSuccess: () => {
      setUploadFile(null);
      void queryClient.invalidateQueries({ queryKey: ['ap', 'doc-ingestions'] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
    },
  });

  return (
    <Card className="mt-8">
      <CardHeader
        title="AP invoice ingestion"
        description="Upload supplier invoices for AI staging, or paste OCR JSON to reconcile against PO lines"
      />
      <div className="space-y-4 p-4">
        <div className="space-y-3" data-testid="document-ai-upload">
          <p className="text-sm font-medium text-text">Document AI upload</p>
          <div
            className={cn(
              'flex min-h-40 cursor-pointer flex-col items-center justify-center gap-3 rounded-lg border border-dashed px-4 py-8 text-center transition-colors',
              dragOver ? 'border-accent bg-accent/5' : 'border-border bg-surface-overlay/40',
              uploadMutation.isPending && 'pointer-events-none opacity-60',
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
              const file = e.dataTransfer.files?.[0] ?? null;
              if (file) setUploadFile(file);
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
              {uploadFile ? (
                <span className="font-medium">{uploadFile.name}</span>
              ) : (
                <>
                  Drop a PDF or image here, or <span className="text-accent">browse</span>
                </>
              )}
            </div>
            <p className="text-xs text-text-muted">PDF, CSV, TXT, or image — AI stages lines for PO match</p>
            <input
              id="ap-invoice-file-input"
              type="file"
              accept=".pdf,.txt,.csv,image/*"
              className="hidden"
              onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)}
            />
          </div>
          <Button
            className="min-h-11 touch-target"
            data-testid="document-ai-upload-btn"
            onClick={() => uploadFile && uploadMutation.mutate(uploadFile)}
            loading={uploadMutation.isPending}
            disabled={!uploadFile}
          >
            Upload invoice document
          </Button>
          {docIngestions.length > 0 && (
            <div className="space-y-2">
              {docIngestions.slice(0, 5).map((ing) => (
                <div key={ing.id} className="rounded-lg border border-border p-3 text-sm">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium">{ing.ingestionStatus}</span>
                    <span className="truncate text-xs text-text-muted">{ing.fileStorageKey}</span>
                  </div>
                  {ing.matchedPurchaseOrderId && (
                    <p className="mt-1 text-xs text-text-muted">
                      Matched PO {ing.matchedPurchaseOrderId.slice(0, 8)}…
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        <Select
          label="Purchase order"
          value={poId}
          onChange={(e) => setPoId(e.target.value)}
        >
          <option value="">Select PO…</option>
          {purchaseOrders.map((po) => (
            <option key={po.id} value={po.id}>
              {po.number}
            </option>
          ))}
        </Select>
        <Input
          label="Document URL"
          value={documentUrl}
          onChange={(e) => setDocumentUrl(e.target.value)}
          placeholder="https://…/supplier-invoice.pdf"
        />
        <label className="block text-sm font-medium text-text">
          Extracted invoice JSON
          <textarea
            className="mt-1 w-full rounded-lg border border-border bg-surface p-3 font-mono text-sm text-text"
            rows={6}
            value={jsonPayload}
            onChange={(e) => setJsonPayload(e.target.value)}
          />
        </label>
        <Button
          className="min-h-11 touch-target"
          onClick={() => submitMutation.mutate()}
          loading={submitMutation.isPending}
          disabled={!poId}
        >
          Upload & reconcile
        </Button>
        {ingestions.length > 0 && (
          <div className="space-y-2">
            <p className="text-sm font-medium text-text">Recent reconciliations</p>
            {ingestions.slice(0, 5).map((ing) => (
              <div
                key={ing.id}
                className={cn(
                  'rounded-lg border p-3 text-sm',
                  ing.status === 'CONFLICT' ? 'border-warning bg-warning/10' : 'border-border'
                )}
              >
                <div className="flex items-center justify-between">
                  <span className="font-medium">{ing.status}</span>
                  <span className="text-text-muted">{ing.matchConfidence.toFixed(0)}% match</span>
                </div>
                {ing.documentUrl && (
                  <p className="mt-1 truncate text-xs text-text-muted">{ing.documentUrl}</p>
                )}
                {ing.status === 'CONFLICT' && (
                  <p className="mt-1 text-xs text-warning">Line conflicts require review</p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}

export function PurchaseOrdersPage() {
  const navigate = useNavigate();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const canReceive = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER');
  const [modalOpen, setModalOpen] = useState(false);
  const [peekPoId, setPeekPoId] = useState<string | null>(null);
  const [receivePoId, setReceivePoId] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } =
    useListQuery<PurchaseOrder>(['purchase-orders'], '/api/v1/purchase-orders');

  const peekPo = data?.find((po) => po.id === peekPoId) ?? null;

  return (
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
        <DataListToolbar />
      </div>

      <div
        className="min-h-0 min-w-0 flex-1 overflow-auto"
        data-list-scrollport="true"
        data-tour="tour-po-grid"
      >
        <ListPageState
          isLoading={isLoading}
          isError={isError}
          error={error}
          data={data}
          refetch={refetch}
          emptyIcon={ClipboardList}
          emptyTitle="No purchase orders yet"
          emptyDescription={
            canCreate
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
            </div>
          )}
        </ListPageState>

        {canCreate && (
          <div className="border-t border-border/60 px-6 pb-6">
            <ApIngestionPanel purchaseOrders={data ?? []} />
          </div>
        )}
      </div>

      <CreatePoModal open={modalOpen} onClose={() => setModalOpen(false)} />
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
                <dt className="text-text-muted">Expected</dt>
                <dd>
                  {peekPo.expectedAt ? new Date(peekPo.expectedAt).toLocaleDateString() : '—'}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Freight</dt>
                <dd className="font-mono tabular-nums">
                  {peekPo.freightAmount != null ? peekPo.freightAmount.toFixed(2) : '—'}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-text-muted">Duties</dt>
                <dd className="font-mono tabular-nums">
                  {peekPo.dutiesAmount != null ? peekPo.dutiesAmount.toFixed(2) : '—'}
                </dd>
              </div>
            </dl>
            {canReceive && RECEIVABLE.has(peekPo.status) && (
              <Button
                className="w-full"
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
  );
}

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AlertTriangle, FileUp, RotateCw, ZoomIn, ZoomOut } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PurchaseOrder } from '@/api/types';
import { unwrapPageItems } from '@/api/page';
import { listPurchaseOrders } from '@/api/operational';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useToast } from '@/components/ui/Toast';
import { takeApInvoiceFile } from '@/features/purchasing/apInvoiceFileStore';
import { cn } from '@/lib/utils';

type MatchStatus = 'MATCHED' | 'QTY_VARIANCE' | 'PRICE_VARIANCE';

type WorkspaceLine = {
  sku: string;
  description: string;
  poQty: number;
  receivedQty: number;
  invoicedQty: number;
  poUnitPrice: number;
  invoicedPrice: number;
  matchStatus: MatchStatus;
  lowConfidence: boolean;
  confidence: number;
};

type WorkspaceHeader = {
  invoiceNumber?: string | null;
  invoiceDate?: string | null;
  supplierName?: string | null;
  subtotal?: number | null;
  tax?: number | null;
  detectedPoNumber?: string | null;
};

type Workspace = {
  ingestionId?: string | null;
  purchaseOrderId?: string | null;
  purchaseOrderNumber?: string | null;
  status: string;
  header: WorkspaceHeader;
  lines: WorkspaceLine[];
  allMatched: boolean;
  hasPriceVariance: boolean;
  hasQtyVariance: boolean;
  receivedLessThanInvoiced: boolean;
};

const QTY_TOL = 0.01;
const PRICE_TOL_PCT = 5;

function lineStatus(line: WorkspaceLine): MatchStatus {
  const qtyOk =
    Math.abs(line.invoicedQty - line.poQty) <= QTY_TOL &&
    Math.abs(line.receivedQty - line.poQty) <= QTY_TOL;
  const priceOk =
    line.poUnitPrice === 0 ||
    (Math.abs(line.invoicedPrice - line.poUnitPrice) * 100) / line.poUnitPrice <= PRICE_TOL_PCT;
  if (qtyOk && priceOk) return 'MATCHED';
  if (!priceOk) return 'PRICE_VARIANCE';
  return 'QTY_VARIANCE';
}

function MatchBadge({ status }: { status: MatchStatus }) {
  const label =
    status === 'MATCHED' ? 'Matched' : status === 'PRICE_VARIANCE' ? 'Price Variance' : 'Qty Variance';
  return (
    <span
      data-testid="ap-match-status"
      data-status={status}
      className={cn(
        'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
        status === 'MATCHED' && 'bg-success/10 text-success',
        status === 'QTY_VARIANCE' && 'bg-warning/10 text-warning',
        status === 'PRICE_VARIANCE' && 'bg-danger/10 text-danger',
      )}
    >
      {label}
    </span>
  );
}

export function ApDocumentWorkspace() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [extracted, setExtracted] = useState<Record<string, unknown>>({});
  const [poQuery, setPoQuery] = useState('');
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const drag = useRef<{ x: number; y: number } | null>(null);
  const [disputeLetter, setDisputeLetter] = useState<string | null>(null);

  const { data: purchaseOrders = [] } = useQuery({
    queryKey: ['purchase-orders', 'ap-workspace'],
    queryFn: async () => {
      const page = await listPurchaseOrders({ page: 1, size: 100, sort: 'createdAt,desc' });
      return unwrapPageItems<PurchaseOrder>(page);
    },
  });

  const ingestMutation = useMutation({
    mutationFn: async (next: File) => {
      const form = new FormData();
      form.append('file', next);
      return (await apiClient.post<Workspace>('/api/v1/ap/ingest', form)).data;
    },
    onSuccess: (data) => applyWorkspace(data),
    onError: () => toast('Could not read the invoice. Try a clearer PDF or image.', { tone: 'danger' }),
  });

  const bindMutation = useMutation({
    mutationFn: async (purchaseOrderId: string) =>
      (
        await apiClient.post<Workspace>('/api/v1/ap/workspace/bind', {
          purchaseOrderId,
          extractedData: extracted,
        })
      ).data,
    onSuccess: (data) => applyWorkspace(data),
  });

  const approveMutation = useMutation({
    mutationFn: async () => {
      if (!workspace?.ingestionId) throw new Error('missing');
      return (
        await apiClient.post<Workspace>(`/api/v1/ap/ingestions/${workspace.ingestionId}/approve`, {
          lines: workspace.lines.map((line) => ({
            sku: line.sku,
            qty: line.invoicedQty,
            unitCost: line.invoicedPrice,
          })),
        })
      ).data;
    },
    onSuccess: (data) => {
      applyWorkspace(data);
      toast('Invoice approved. Voucher is ready for payment.', { tone: 'success' });
    },
    onError: () => toast('Approve requires every line to match within tolerance.', { tone: 'danger' }),
  });

  const disputeMutation = useMutation({
    mutationFn: async () => {
      if (!workspace?.ingestionId) throw new Error('missing');
      return (
        await apiClient.post<{ disputeLetter: string; rtvPath: string }>(
          `/api/v1/ap/ingestions/${workspace.ingestionId}/dispute`,
        )
      ).data;
    },
    onSuccess: (data) => {
      setDisputeLetter(data.disputeLetter);
      toast('Debit memo draft is ready. Open RTV to finish the chargeback.', { tone: 'success' });
    },
  });

  const recountMutation = useMutation({
    mutationFn: async () => {
      if (!workspace?.ingestionId) throw new Error('missing');
      return (
        await apiClient.post<{ variancePath: string }>(
          `/api/v1/ap/ingestions/${workspace.ingestionId}/request-recount`,
        )
      ).data;
    },
    onSuccess: (data) => {
      toast('Warehouse recount opened in Variance Approval.', { tone: 'success' });
      navigate(data.variancePath);
    },
  });

  function applyWorkspace(data: Workspace) {
    const lines = (data.lines ?? []).map((line) => {
      const next = { ...line, matchStatus: lineStatus(line) };
      return next;
    });
    const allMatched = lines.length > 0 && lines.every((l) => l.matchStatus === 'MATCHED');
    setWorkspace({
      ...data,
      lines,
      allMatched,
      hasPriceVariance: lines.some((l) => l.matchStatus === 'PRICE_VARIANCE'),
      hasQtyVariance: lines.some((l) => l.matchStatus === 'QTY_VARIANCE'),
      receivedLessThanInvoiced: lines.some((l) => l.receivedQty + QTY_TOL < l.invoicedQty),
    });
    setExtracted({
      invoiceNumber: data.header?.invoiceNumber,
      invoiceDate: data.header?.invoiceDate,
      supplierName: data.header?.supplierName,
      subtotal: data.header?.subtotal,
      tax: data.header?.tax,
      detectedPoNumber: data.header?.detectedPoNumber,
      lines: lines.map((line) => ({
        sku: line.sku,
        qty: line.invoicedQty,
        unitCost: line.invoicedPrice,
        confidence: line.confidence,
      })),
    });
    if (data.purchaseOrderNumber) setPoQuery(data.purchaseOrderNumber);
  }

  const attachFile = useCallback(
    (next: File | null) => {
      if (!next) return;
      setFile(next);
      setDisputeLetter(null);
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      setPreviewUrl(URL.createObjectURL(next));
      setZoom(1);
      setRotation(0);
      setPan({ x: 0, y: 0 });
      ingestMutation.mutate(next);
    },
    [previewUrl, ingestMutation],
  );

  useEffect(() => {
    const stashed = takeApInvoiceFile();
    if (stashed) attachFile(stashed);
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
    // first mount only
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredPos = useMemo(() => {
    const q = poQuery.trim().toLowerCase();
    if (!q) return purchaseOrders.slice(0, 12);
    return purchaseOrders
      .filter((po) => `${po.number} ${po.supplierName ?? ''}`.toLowerCase().includes(q))
      .slice(0, 12);
  }, [poQuery, purchaseOrders]);

  const header = workspace?.header;
  const lines = workspace?.lines ?? [];
  const overbill = Boolean(workspace?.hasPriceVariance || workspace?.hasQtyVariance);
  const showApprove = Boolean(workspace?.ingestionId && workspace.allMatched);
  const showDispute = Boolean(workspace?.ingestionId && overbill);
  const showRecount = Boolean(workspace?.ingestionId && workspace.receivedLessThanInvoiced);

  function patchLine(index: number, patch: Partial<WorkspaceLine>) {
    if (!workspace) return;
    const nextLines = workspace.lines.map((line, i) => {
      if (i !== index) return line;
      const merged = { ...line, ...patch };
      return { ...merged, matchStatus: lineStatus(merged) };
    });
    applyWorkspace({ ...workspace, lines: nextLines });
  }

  function updateHeader(patch: Partial<WorkspaceHeader>) {
    if (!workspace) return;
    setWorkspace({ ...workspace, header: { ...workspace.header, ...patch } });
    setExtracted((prev) => ({ ...prev, ...patch }));
  }

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="ap-document-workspace">
      <header className="flex items-center justify-between gap-4 border-b border-border px-6 py-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-text-muted">weGrowStock · Inbound</p>
          <h1 className="text-lg font-semibold text-text">AP Invoice Reconciliation</h1>
        </div>
        <Button variant="ghost" onClick={() => navigate('/purchase-orders')}>
          Back to purchase orders
        </Button>
      </header>

      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-2">
        <section
          className="relative flex min-h-[22rem] flex-col border-b border-border lg:border-b-0 lg:border-r"
          data-testid="ap-document-viewer"
        >
          {!file ? (
            <div
              className="m-6 flex min-h-80 flex-1 cursor-pointer flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-surface-overlay/40 px-6 text-center"
              data-testid="document-ai-dropzone"
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                attachFile(e.dataTransfer.files?.[0] ?? null);
              }}
              onClick={() => document.getElementById('ap-workspace-file')?.click()}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  document.getElementById('ap-workspace-file')?.click();
                }
              }}
              role="button"
              tabIndex={0}
              aria-label="Drop invoice document or browse"
            >
              <FileUp className="h-8 w-8 text-text-muted" aria-hidden />
              <p className="text-sm text-text">
                Drop a PDF or image here, or <span className="text-accent">browse</span>
              </p>
              <p className="text-xs text-text-muted">OCR fills the invoice form. No JSON paste.</p>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-2 border-b border-border px-4 py-2">
                <span className="truncate text-sm font-medium">{file.name}</span>
                <div className="ml-auto flex items-center gap-1">
                  <Button size="sm" variant="ghost" onClick={() => setZoom((z) => Math.max(0.4, z - 0.2))} aria-label="Zoom out">
                    <ZoomOut className="h-4 w-4" />
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setZoom((z) => Math.min(3, z + 0.2))} aria-label="Zoom in">
                    <ZoomIn className="h-4 w-4" />
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setRotation((r) => (r + 90) % 360)} aria-label="Rotate">
                    <RotateCw className="h-4 w-4" />
                  </Button>
                </div>
              </div>
              <div
                className="relative min-h-0 flex-1 overflow-hidden bg-surface-overlay/30"
                onMouseDown={(e) => {
                  drag.current = { x: e.clientX - pan.x, y: e.clientY - pan.y };
                }}
                onMouseMove={(e) => {
                  if (!drag.current) return;
                  setPan({ x: e.clientX - drag.current.x, y: e.clientY - drag.current.y });
                }}
                onMouseUp={() => {
                  drag.current = null;
                }}
                onMouseLeave={() => {
                  drag.current = null;
                }}
              >
                {previewUrl && file.type.startsWith('image/') ? (
                  <img
                    src={previewUrl}
                    alt="Invoice preview"
                    className="absolute left-1/2 top-1/2 max-h-none select-none"
                    style={{
                      transform: `translate(-50%, -50%) translate(${pan.x}px, ${pan.y}px) rotate(${rotation}deg) scale(${zoom})`,
                    }}
                    draggable={false}
                  />
                ) : previewUrl ? (
                  <iframe
                    title="Invoice PDF"
                    src={previewUrl}
                    className="h-full w-full border-0 bg-white"
                    style={{
                      transform: `translate(${pan.x}px, ${pan.y}px) rotate(${rotation}deg) scale(${zoom})`,
                      transformOrigin: 'center center',
                    }}
                  />
                ) : null}
              </div>
            </>
          )}
          <input
            id="ap-workspace-file"
            type="file"
            accept=".pdf,.txt,.csv,image/*"
            className="hidden"
            data-testid="ap-invoice-file-input"
            onChange={(e) => attachFile(e.target.files?.[0] ?? null)}
          />
        </section>

        <section className="flex min-h-0 flex-col overflow-auto px-6 py-5" data-testid="ap-review-form">
          {ingestMutation.isPending && (
            <p className="mb-4 text-sm text-text-muted">Reading invoice…</p>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
            <Input
              label="Invoice number"
              value={header?.invoiceNumber ?? ''}
              onChange={(e) => updateHeader({ invoiceNumber: e.target.value })}
              data-testid="ap-invoice-number"
            />
            <Input
              label="Invoice date"
              value={header?.invoiceDate ?? ''}
              onChange={(e) => updateHeader({ invoiceDate: e.target.value })}
              data-testid="ap-invoice-date"
            />
            <Input
              label="Supplier name"
              value={header?.supplierName ?? ''}
              onChange={(e) => updateHeader({ supplierName: e.target.value })}
              data-testid="ap-supplier-name"
            />
            <Input
              label="Detected PO"
              value={header?.detectedPoNumber ?? workspace?.purchaseOrderNumber ?? ''}
              readOnly
              data-testid="ap-detected-po"
            />
            <Input
              label="Subtotal"
              value={header?.subtotal != null ? String(header.subtotal) : ''}
              onChange={(e) => updateHeader({ subtotal: Number(e.target.value) || 0 })}
            />
            <Input
              label="Tax"
              value={header?.tax != null ? String(header.tax) : ''}
              onChange={(e) => updateHeader({ tax: Number(e.target.value) || 0 })}
            />
          </div>

          {!workspace?.purchaseOrderId && (
            <div className="mt-5" data-testid="ap-po-combobox">
              <Input
                label="Link purchase order"
                placeholder="Search PO number or supplier…"
                value={poQuery}
                onChange={(e) => setPoQuery(e.target.value)}
              />
              <ul className="mt-2 max-h-40 overflow-auto rounded-lg border border-border">
                {filteredPos.map((po) => (
                  <li key={po.id}>
                    <button
                      type="button"
                      className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-surface-overlay"
                      onClick={() => bindMutation.mutate(po.id)}
                    >
                      <span className="font-medium">{po.number}</span>
                      <span className="text-text-muted">{po.supplierName ?? po.status}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <h2 className="mt-6 text-sm font-semibold text-text">3-way match</h2>
          <div className="mt-2 overflow-x-auto rounded-xl border border-border">
            <div data-testid="ap-three-way-table">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>SKU / Description</TableHead>
                  <TableHead>PO Qty</TableHead>
                  <TableHead>Received Qty</TableHead>
                  <TableHead>Invoiced Qty</TableHead>
                  <TableHead>PO Unit Price</TableHead>
                  <TableHead>Invoiced Price</TableHead>
                  <TableHead>Match Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lines.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-text-muted">
                      Drop an invoice to populate lines. Raw JSON is not used.
                    </TableCell>
                  </TableRow>
                ) : (
                  lines.map((line, index) => (
                    <TableRow key={`${line.sku}-${index}`}>
                      <TableCell>
                        <div className="font-medium">{line.sku}</div>
                        <div className="text-xs text-text-muted">{line.description}</div>
                      </TableCell>
                      <TableCell className="font-mono tabular-nums">{line.poQty}</TableCell>
                      <TableCell className="font-mono tabular-nums">{line.receivedQty}</TableCell>
                      <TableCell>
                        <input
                          className="h-9 w-24 rounded-md border border-border bg-surface px-2 font-mono text-sm"
                          data-testid="ap-invoiced-qty"
                          type="number"
                          step="0.01"
                          value={line.invoicedQty}
                          onChange={(e) => patchLine(index, { invoicedQty: Number(e.target.value) })}
                        />
                        {line.lowConfidence && (
                          <AlertTriangle className="ml-1 inline h-3.5 w-3.5 text-warning" aria-label="Low OCR confidence" />
                        )}
                      </TableCell>
                      <TableCell className="font-mono tabular-nums">{Number(line.poUnitPrice).toFixed(2)}</TableCell>
                      <TableCell>
                        <input
                          className="h-9 w-24 rounded-md border border-border bg-surface px-2 font-mono text-sm"
                          data-testid="ap-invoiced-price"
                          type="number"
                          step="0.01"
                          value={line.invoicedPrice}
                          onChange={(e) => patchLine(index, { invoicedPrice: Number(e.target.value) })}
                        />
                        {line.lowConfidence && (
                          <AlertTriangle className="ml-1 inline h-3.5 w-3.5 text-warning" aria-label="Low OCR confidence" />
                        )}
                      </TableCell>
                      <TableCell>
                        <MatchBadge status={line.matchStatus} />
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
            </div>
          </div>
          {disputeLetter && (
            <pre
              className="mt-4 whitespace-pre-wrap rounded-lg border border-border bg-surface-overlay/40 p-3 text-xs"
              data-testid="ap-dispute-letter"
            >
              {disputeLetter}
            </pre>
          )}
        </section>
      </div>

      <footer className="flex flex-wrap items-center gap-3 border-t border-border px-6 py-4" data-testid="ap-workspace-actions">
        {showApprove && (
          <Button
            data-testid="ap-approve-match"
            onClick={() => approveMutation.mutate()}
            loading={approveMutation.isPending}
          >
            Approve & Match
          </Button>
        )}
        {showDispute && (
          <>
            <Button
              variant="secondary"
              data-testid="ap-issue-debit-memo"
              onClick={() => disputeMutation.mutate()}
              loading={disputeMutation.isPending}
            >
              Issue Debit Memo / Dispute
            </Button>
            <Link to="/purchasing/rtv" className="text-sm text-accent hover:underline">
              Open RTV
            </Link>
          </>
        )}
        {showRecount && (
          <Button
            variant="secondary"
            data-testid="ap-request-recount"
            onClick={() => recountMutation.mutate()}
            loading={recountMutation.isPending}
          >
            Request Warehouse Recount
          </Button>
        )}
        {!workspace && (
          <p className="text-sm text-text-muted">Upload a vendor invoice to start 3-way matching.</p>
        )}
      </footer>
    </div>
  );
}

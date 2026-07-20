import { useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, Package, Warehouse } from 'lucide-react';
import { Link } from 'react-router-dom';
import { apiClient } from '@/api/client';
import { BarcodeScannerInput } from '@/features/inbound/BarcodeScannerInput';
import { BigButton } from '@/components/ui/BigButton';
import { NetworkStatusBadge } from '@/components/layout/NetworkStatusBadge';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { enqueueScanMutation } from '@/offline/mutationQueue';
import { createScanEventPayload } from '@/offline/scanEvent';

type Step = 'po' | 'item' | 'qty' | 'putaway' | 'done';

interface InboundLine {
  lineId: string;
  variantId: string;
  sku: string | null;
  barcode: string | null;
  qtyOrdered: number | string;
  qtyReceived: number | string;
  qtyRemaining: number | string;
}

interface InboundPo {
  id: string;
  number: string;
  status: string;
  lines: InboundLine[];
}

interface LineMatch {
  lineId: string;
  variantId: string;
  sku: string;
  barcode: string | null;
  qtyOrdered: number | string;
  qtyReceived: number | string;
  qtyRemaining: number | string;
}

interface PutawayDirective {
  locationId: string;
  path: string;
  code: string;
  strategy: string;
  instruction: string;
  aisle: string | null;
  rack: string | null;
  binLabel: string;
}

function num(v: number | string | undefined): number {
  return Number(v ?? 0);
}

/**
 * Full-screen mobile inbound receiving + directed putaway (Zebra / Honeywell).
 */
export function InboundReceivePage() {
  const { flash, triggerSuccess, triggerError, triggerPendingSync } = useScanFeedback();
  const [step, setStep] = useState<Step>('po');
  const [lastScan, setLastScan] = useState<string | null>(null);
  const [pendingSyncLabel, setPendingSyncLabel] = useState(false);
  const [po, setPo] = useState<InboundPo | null>(null);
  const [match, setMatch] = useState<LineMatch | null>(null);
  const [qty, setQty] = useState('1');
  const [putaway, setPutaway] = useState<PutawayDirective | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmResult, setConfirmResult] = useState<{
    poNumber: string;
    locationCode: string;
    quantityChange: number | string;
    action: string;
  } | null>(null);
  /** Optimistic local counter so floor workers keep moving while offline. */
  const [scannedCount, setScannedCount] = useState(0);

  const lookupPo = useMutation({
    networkMode: 'offlineFirst',
    mutationFn: async (barcode: string) =>
      (await apiClient.get<InboundPo>('/api/v1/inbound/receive/po', { params: { barcode } })).data,
    onSuccess: (data) => {
      triggerSuccess();
      setPo(data);
      setStep('item');
      setError(null);
    },
    onError: () => {
      triggerError();
      setError('PO / ASN not found or not receivable.');
    },
  });

  const resolveItem = useMutation({
    networkMode: 'offlineFirst',
    mutationFn: async (barcode: string) =>
      (
        await apiClient.post<LineMatch>('/api/v1/inbound/receive/resolve-item', {
          poId: po!.id,
          barcode,
        })
      ).data,
    onSuccess: async (data) => {
      triggerSuccess();
      setMatch(data);
      setQty(String(num(data.qtyRemaining)));
      setError(null);
      try {
        const suggestion = (
          await apiClient.get<PutawayDirective>('/api/v1/inbound/receive/putaway-suggestion', {
            params: { variantId: data.variantId },
          })
        ).data;
        setPutaway(suggestion);
      } catch {
        setPutaway(null);
      }
      setStep('qty');
    },
    onError: () => {
      triggerError();
      setError('Item not on this PO or already fully received.');
    },
  });

  const confirmPutaway = useMutation({
    networkMode: 'offlineFirst',
    mutationFn: async (scannedLocationBarcode: string) => {
      const body = {
        lineId: match!.lineId,
        quantity: Number(qty),
        locationId: putaway?.locationId,
        scannedLocationBarcode,
      };
      const scanEvent = createScanEventPayload(scannedLocationBarcode);
      if (!navigator.onLine) {
        await enqueueScanMutation(scanEvent, {
          method: 'POST',
          url: '/api/v1/inbound/receive/confirm',
          body,
        });
        return {
          poNumber: po!.number,
          locationCode: putaway?.code ?? scannedLocationBarcode,
          quantityChange: Number(qty),
          action: 'PO_RECEIPT_QUEUED',
        };
      }
      return (
        await apiClient.post<{
          poNumber: string;
          locationCode: string;
          quantityChange: number | string;
          action: string;
        }>('/api/v1/inbound/receive/confirm', body, {
          headers: { 'Idempotency-Key': scanEvent.idempotencyKey },
        })
      ).data;
    },
    onMutate: async () => {
      const qtyNum = Number(qty);
      setScannedCount((c) => c + 1);
      if (!navigator.onLine) {
        triggerPendingSync();
        setPendingSyncLabel(true);
        window.setTimeout(() => setPendingSyncLabel(false), 1200);
      }
      if (match && po) {
        setPo((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            lines: prev.lines.map((line) =>
              line.lineId === match.lineId
                ? {
                    ...line,
                    qtyReceived: num(line.qtyReceived) + qtyNum,
                    qtyRemaining: Math.max(0, num(line.qtyRemaining) - qtyNum),
                  }
                : line,
            ),
          };
        });
        setMatch((prev) =>
          prev
            ? {
                ...prev,
                qtyReceived: num(prev.qtyReceived) + qtyNum,
                qtyRemaining: Math.max(0, num(prev.qtyRemaining) - qtyNum),
              }
            : prev,
        );
      }
    },
    onSuccess: (data) => {
      if (data.action === 'PO_RECEIPT_QUEUED') {
        // Pending flash already fired in onMutate for offline path.
      } else {
        triggerSuccess();
      }
      setConfirmResult(data);
      setStep('done');
      setError(null);
    },
    onError: () => {
      triggerError();
      setPendingSyncLabel(false);
      setScannedCount((c) => Math.max(0, c - 1));
      setError('Putaway confirm failed. Scan the directed bin.');
    },
  });

  const remaining = useMemo(() => (match ? num(match.qtyRemaining) : 0), [match]);

  function onScan(barcode: string) {
    setLastScan(barcode);
    setError(null);
    if (step === 'po') {
      lookupPo.mutate(barcode);
      return;
    }
    if (step === 'item') {
      resolveItem.mutate(barcode);
      return;
    }
    if (step === 'putaway') {
      confirmPutaway.mutate(barcode);
    }
  }

  function reset() {
    setStep('po');
    setPo(null);
    setMatch(null);
    setPutaway(null);
    setConfirmResult(null);
    setQty('1');
    setLastScan(null);
    setError(null);
  }

  return (
    <div
      className="flex min-h-dvh flex-col bg-surface px-4 py-5 pb-10 text-text"
      data-theme="warehouse"
      data-testid="inbound-receive-page"
      data-tour="inbound-receive"
    >
      <ScanFlashOverlay flash={flash} />

      <header className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/fulfillment"
          className="inline-flex items-center gap-2 text-sm font-medium text-text-muted"
          data-testid="inbound-back-link"
        >
          <ArrowLeft className="h-4 w-4" /> Floor
        </Link>
        <div className="flex flex-col items-end gap-2">
          <NetworkStatusBadge />
          <div className="text-right">
            <p className="text-xs font-semibold uppercase tracking-wide text-text-muted">Inbound</p>
            <h1 className="text-lg font-bold text-text">Receive & Putaway</h1>
            {scannedCount > 0 && (
              <p className="mt-1 text-xs font-medium text-accent" data-testid="inbound-scanned-count">
                Scanned {scannedCount}
              </p>
            )}
            {pendingSyncLabel && (
              <p
                className="mt-1 text-xs font-semibold uppercase tracking-wide text-warning"
                data-testid="inbound-pending-sync"
              >
                Pending Sync
              </p>
            )}
          </div>
        </div>
      </header>

      {error && (
        <p className="mb-4 rounded-lg bg-danger/15 px-3 py-2 text-sm text-danger" data-testid="inbound-error">
          {error}
        </p>
      )}

      {step === 'po' && (
        <section className="space-y-4" data-testid="inbound-step-po">
          <BarcodeScannerInput
            label="Scan PO or ASN"
            hint="Scan the purchase order or advanced shipping notice barcode"
            onScan={onScan}
            lastScan={lastScan}
          />
        </section>
      )}

      {step === 'item' && po && (
        <section className="space-y-4" data-testid="inbound-step-item">
          <div className="rounded-xl border border-border bg-surface-raised p-4">
            <p className="font-mono text-xl font-bold">{po.number}</p>
            <p className="text-sm text-text-muted">Status {po.status}</p>
            <ul className="mt-3 space-y-2" data-testid="inbound-expected-lines">
              {po.lines.map((line) => (
                <li
                  key={line.lineId}
                  className="flex items-center justify-between rounded-lg bg-surface-overlay/50 px-3 py-2 text-sm"
                >
                  <span className="font-mono font-semibold">{line.sku ?? line.variantId.slice(0, 8)}</span>
                  <span className="tabular-nums text-text-muted">
                    {num(line.qtyReceived)}/{num(line.qtyOrdered)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
          <BarcodeScannerInput
            label="Scan item UPC / EAN"
            hint="Scan the product barcode on the pallet or case"
            onScan={onScan}
            lastScan={lastScan}
          />
        </section>
      )}

      {step === 'qty' && match && (
        <section className="space-y-4" data-testid="inbound-step-qty">
          <div className="rounded-xl border border-border bg-surface-raised p-4">
            <div className="mb-2 flex items-center gap-2">
              <Package className="h-5 w-5 text-accent" />
              <p className="font-mono text-lg font-bold">{match.sku}</p>
            </div>
            <p className="text-sm text-text-muted">Remaining on ASN: {remaining}</p>
          </div>
          <label className="block">
            <span className="mb-2 block text-sm font-medium text-text-muted">Quantity received</span>
            <input
              type="number"
              min={0.0001}
              step="any"
              value={qty}
              onChange={(e) => setQty(e.target.value)}
              className="h-14 w-full rounded-xl border-2 border-border bg-surface-raised px-4 text-center font-mono text-2xl font-bold"
              data-testid="inbound-qty-input"
              aria-label="Quantity received"
            />
          </label>
          <BigButton
            type="button"
            variant="secondary"
            onClick={() => setQty(String(remaining))}
            data-testid="inbound-receive-all"
          >
            Receive All ({remaining})
          </BigButton>
          <BigButton
            type="button"
            onClick={() => setStep('putaway')}
            disabled={!qty || Number(qty) <= 0}
            data-testid="inbound-qty-continue"
          >
            Continue to Putaway
          </BigButton>
        </section>
      )}

      {step === 'putaway' && putaway && (
        <section className="space-y-4" data-testid="inbound-step-putaway">
          <div className="rounded-2xl border-4 border-accent bg-accent-muted p-5 text-center">
            <Warehouse className="mx-auto mb-3 h-10 w-10 text-accent" />
            <p className="text-xs font-bold uppercase tracking-wide text-accent">Directed Putaway</p>
            <p className="mt-2 text-3xl font-black tracking-tight text-text" data-testid="putaway-bin-label">
              {putaway.binLabel}
            </p>
            <p className="sr-only" data-testid="putaway-code">
              {putaway.code}
            </p>
            <p className="mt-2 font-mono text-sm text-text" data-testid="putaway-path">
              {putaway.path}
            </p>
            <p className="mt-3 text-sm text-text-muted" data-testid="putaway-instruction">
              {putaway.instruction}
            </p>
            <dl className="mt-4 grid grid-cols-3 gap-2 text-xs uppercase tracking-wide text-text-muted">
              <div>
                <dt>Aisle</dt>
                <dd className="font-mono text-base font-bold text-text">{putaway.aisle ?? '—'}</dd>
              </div>
              <div>
                <dt>Rack</dt>
                <dd className="font-mono text-base font-bold text-text">{putaway.rack ?? '—'}</dd>
              </div>
              <div>
                <dt>Bin</dt>
                <dd className="font-mono text-base font-bold text-text">{putaway.binLabel}</dd>
              </div>
            </dl>
            <p className="mt-3 font-mono text-[11px] text-accent" data-testid="putaway-strategy">
              {putaway.strategy}
            </p>
          </div>
          <BarcodeScannerInput
            label="Confirm putaway — scan bin"
            hint={`Drive to ${putaway.code} and scan the bin label`}
            onScan={onScan}
            lastScan={lastScan}
          />
        </section>
      )}

      {step === 'putaway' && !putaway && (
        <p className="text-sm text-danger">No putaway location available for this SKU.</p>
      )}

      {step === 'done' && confirmResult && (
        <section className="space-y-4 text-center" data-testid="inbound-step-done">
          <CheckCircle2 className="mx-auto h-16 w-16 text-success" />
          <h2 className="text-2xl font-bold">Received</h2>
          <p className="font-mono text-sm text-text-muted">
            {confirmResult.action} · PO {confirmResult.poNumber} · +{confirmResult.quantityChange} @{' '}
            {confirmResult.locationCode}
          </p>
          <BigButton type="button" onClick={reset} data-testid="inbound-receive-another">
            Receive another line
          </BigButton>
        </section>
      )}
    </div>
  );
}

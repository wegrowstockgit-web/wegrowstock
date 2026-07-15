import { useMemo, useState, type ReactNode } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Download, GitBranch, MapPin, Package, ScanLine, Truck } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { ComplianceLotTraceResponse } from '@/api/types';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils';

function exportAffectedCustomersCsv(trace: ComplianceLotTraceResponse) {
  const rows = [
    ['customer_name', 'customer_id', 'sales_order', 'quantity', 'shipped_at', 'tracking_number'],
    ...trace.downstream.map((d) => [
      d.customerName ?? '',
      d.customerId ?? '',
      d.salesOrderNumber ?? '',
      d.quantity != null ? String(d.quantity) : '',
      d.shippedAt ?? '',
      d.trackingNumber ?? '',
    ]),
  ];
  const csv = rows
    .map((r) => r.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    .join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `lot-recall-${trace.lotNumber}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

function TimelineRail({
  tone,
  children,
}: {
  tone: 'origin' | 'exposure' | 'downstream';
  children: ReactNode;
}) {
  const bar =
    tone === 'origin'
      ? 'bg-accent'
      : tone === 'exposure'
        ? 'bg-warning'
        : 'bg-danger';
  return (
    <div className="relative pl-6">
      <div className={cn('absolute left-1.5 top-2 bottom-2 w-0.5 rounded-full', bar)} aria-hidden />
      <div className="space-y-3">{children}</div>
    </div>
  );
}

function NodeCard({
  icon,
  title,
  subtitle,
  meta,
}: {
  icon: ReactNode;
  title: string;
  subtitle?: string | null;
  meta?: string | null;
}) {
  return (
    <div className="relative rounded-md border border-border bg-surface-raised px-3 py-2.5">
      <span className="absolute -left-[1.35rem] top-3 flex h-4 w-4 items-center justify-center rounded-full border border-border bg-surface text-text">
        {icon}
      </span>
      <p className="text-sm font-semibold text-text">{title}</p>
      {subtitle && <p className="mt-0.5 text-xs text-text-muted">{subtitle}</p>}
      {meta && <p className="mt-1 font-mono text-xs tabular-nums text-text">{meta}</p>}
    </div>
  );
}

export function LotTraceView() {
  const [lotNumber, setLotNumber] = useState('');
  const [trace, setTrace] = useState<ComplianceLotTraceResponse | null>(null);
  const { triggerSuccess, triggerError } = useScanFeedback();

  const lookupMutation = useMutation({
    mutationFn: async (number: string) => {
      const res = await apiClient.get<ComplianceLotTraceResponse>('/api/v1/compliance/lot-trace', {
        params: { lotNumber: number },
      });
      return res.data;
    },
    onSuccess: (data) => {
      triggerSuccess();
      setTrace(data);
    },
    onError: () => {
      triggerError();
      setTrace(null);
    },
  });

  useHardwareScanner({
    enabled: true,
    captureAll: true,
    onScan: (code) => {
      if (!code.length) return;
      setLotNumber(code);
      lookupMutation.mutate(code);
    },
  });

  const exposureTotal = useMemo(
    () =>
      trace?.currentExposure.reduce((sum, row) => sum + Number(row.onHand ?? 0), 0) ?? 0,
    [trace],
  );

  return (
    <div data-testid="lot-trace-view">
      <div className="mb-6">
        <div className="mb-1 flex items-center gap-2">
          <GitBranch className="h-6 w-6 text-accent" aria-hidden />
          <h1 className="text-2xl font-bold text-text text-wrap-balance">Lot genealogy</h1>
        </div>
        <p className="max-w-2xl text-sm text-text-muted">
          Origin receive, live bin exposure, and downstream shipments for recall readiness.
        </p>
      </div>

      <Card className="mb-6 max-w-xl" padding="md">
        <label className="mb-2 block text-sm font-medium text-text" htmlFor="lot-number">
          Lot number
        </label>
        <div className="flex gap-2">
          <Input
            id="lot-number"
            value={lotNumber}
            onChange={(e) => setLotNumber(e.target.value)}
            placeholder="Scan or type lot number"
            onKeyDown={(e) => {
              if (e.key === 'Enter' && lotNumber.trim()) {
                lookupMutation.mutate(lotNumber.trim());
              }
            }}
          />
          <Button
            disabled={!lotNumber.trim() || lookupMutation.isPending}
            loading={lookupMutation.isPending}
            onClick={() => lookupMutation.mutate(lotNumber.trim())}
          >
            <ScanLine className="h-4 w-4" />
            Trace
          </Button>
        </div>
      </Card>

      {trace && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-mono text-sm text-text-muted">SKU {trace.sku}</p>
              <p className="text-lg font-semibold text-text">Lot {trace.lotNumber}</p>
            </div>
            <Button
              variant="secondary"
              disabled={trace.downstream.length === 0}
              onClick={() => exportAffectedCustomersCsv(trace)}
              data-testid="export-recall-csv"
            >
              <Download className="h-4 w-4" />
              Export affected customers
            </Button>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            <Card padding="md" data-testid="lot-trace-origin">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-text-muted">
                Origin
              </h2>
              <TimelineRail tone="origin">
                {trace.origin ? (
                  <NodeCard
                    icon={<Package className="h-2.5 w-2.5" aria-hidden />}
                    title={
                      trace.origin.purchaseOrderNumber
                        ? `Receive · PO ${trace.origin.purchaseOrderNumber}`
                        : 'Receive'
                    }
                    subtitle={
                      [
                        trace.origin.supplierName && `Supplier ${trace.origin.supplierName}`,
                        trace.origin.locationPath,
                      ]
                        .filter(Boolean)
                        .join(' · ') || null
                    }
                    meta={`Qty ${trace.origin.quantity ?? '—'} · ${
                      trace.origin.receivedAt
                        ? new Date(trace.origin.receivedAt).toLocaleString()
                        : '—'
                    }`}
                  />
                ) : (
                  <p className="text-sm text-text-muted">No RECEIVE ledger entry found for this lot.</p>
                )}
              </TimelineRail>
            </Card>

            <Card padding="md" data-testid="lot-trace-exposure">
              <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-text-muted">
                Current exposure
              </h2>
              <p className="mb-3 font-mono text-xs tabular-nums text-text">
                {trace.currentExposure.length} bin(s) · {exposureTotal} on hand
              </p>
              <TimelineRail tone="exposure">
                {trace.currentExposure.length === 0 ? (
                  <p className="text-sm text-text-muted">No active inventory for this lot.</p>
                ) : (
                  trace.currentExposure.map((bin) => (
                    <NodeCard
                      key={bin.inventoryLevelId}
                      icon={<MapPin className="h-2.5 w-2.5" aria-hidden />}
                      title={bin.locationCode}
                      subtitle={`${bin.locationPath} · ${bin.zoneBehavior ?? bin.locationType}`}
                      meta={`On hand ${bin.onHand} · Available ${bin.available}`}
                    />
                  ))
                )}
              </TimelineRail>
            </Card>

            <Card padding="md" data-testid="lot-trace-downstream">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-text-muted">
                Downstream
              </h2>
              <TimelineRail tone="downstream">
                {trace.downstream.length === 0 ? (
                  <p className="text-sm text-text-muted">No SHIP movements for this lot yet.</p>
                ) : (
                  trace.downstream.map((ship) => (
                    <NodeCard
                      key={ship.ledgerId}
                      icon={<Truck className="h-2.5 w-2.5" aria-hidden />}
                      title={
                        ship.salesOrderNumber
                          ? `Ship · SO ${ship.salesOrderNumber}`
                          : 'Ship'
                      }
                      subtitle={
                        [
                          ship.customerName && `Customer ${ship.customerName}`,
                          ship.trackingNumber && `Track ${ship.trackingNumber}`,
                        ]
                          .filter(Boolean)
                          .join(' · ') || null
                      }
                      meta={`Qty ${ship.quantity ?? '—'} · ${
                        ship.shippedAt ? new Date(ship.shippedAt).toLocaleString() : '—'
                      }`}
                    />
                  ))
                )}
              </TimelineRail>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}

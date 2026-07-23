import { useCallback, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ScanLine } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { ClusterPickStep } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { cn } from '@/lib/utils';

const SLOT_COLORS = [
  'bg-sky-500/25 border-sky-500 text-sky-900 dark:text-sky-100',
  'bg-emerald-500/25 border-emerald-500 text-emerald-900 dark:text-emerald-100',
  'bg-amber-500/25 border-amber-500 text-amber-900 dark:text-amber-100',
  'bg-rose-500/25 border-rose-500 text-rose-900 dark:text-rose-100',
  'bg-violet-500/25 border-violet-500 text-violet-900 dark:text-violet-100',
  'bg-cyan-500/25 border-cyan-500 text-cyan-900 dark:text-cyan-100',
  'bg-orange-500/25 border-orange-500 text-orange-900 dark:text-orange-100',
  'bg-lime-500/25 border-lime-500 text-lime-900 dark:text-lime-100',
  'bg-fuchsia-500/25 border-fuchsia-500 text-fuchsia-900 dark:text-fuchsia-100',
  'bg-teal-500/25 border-teal-500 text-teal-900 dark:text-teal-100',
  'bg-indigo-500/25 border-indigo-500 text-indigo-900 dark:text-indigo-100',
  'bg-pink-500/25 border-pink-500 text-pink-900 dark:text-pink-100',
];

function playCorrectSlotBeep(): void {
  if (typeof window === 'undefined') return;
  const Ctx =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  if (!Ctx) return;
  const ctx = new Ctx();
  const oscillator = ctx.createOscillator();
  const gain = ctx.createGain();
  oscillator.type = 'sine';
  oscillator.frequency.value = 1046;
  gain.gain.value = 0.18;
  oscillator.connect(gain);
  gain.connect(ctx.destination);
  oscillator.start();
  oscillator.stop(ctx.currentTime + 0.12);
  void ctx.close();
}

function slotColor(slotIndex: number): string {
  return SLOT_COLORS[(slotIndex - 1) % SLOT_COLORS.length]!;
}

export function ClusterPickerView() {
  const [batchIdInput, setBatchIdInput] = useState('');
  const [batchId, setBatchId] = useState<string | null>(null);
  const [scanValue, setScanValue] = useState('');
  const [highlightSlot, setHighlightSlot] = useState<number | null>(null);
  const [lastSku, setLastSku] = useState<string | null>(null);
  const [scanError, setScanError] = useState<string | null>(null);

  const { data: steps = [], isLoading } = useQuery({
    queryKey: ['cluster-pick', 'sequence', batchId],
    queryFn: async () =>
      (
        await apiClient.get<ClusterPickStep[]>(
          `/api/v1/fulfillment/cluster/batches/${batchId}/pick-sequence`,
        )
      ).data,
    enabled: batchId != null,
    retry: false,
  });

  const slots = useMemo(() => {
    const map = new Map<number, { toteBarcode: string; skus: string[] }>();
    for (const step of steps) {
      const existing = map.get(step.slotIndex) ?? { toteBarcode: step.toteBarcode, skus: [] };
      if (!existing.skus.includes(step.sku)) {
        existing.skus.push(step.sku);
      }
      existing.toteBarcode = step.toteBarcode;
      map.set(step.slotIndex, existing);
    }
    return map;
  }, [steps]);

  const handleScan = useCallback(
    (raw: string) => {
      const barcode = raw.trim();
      if (!barcode || !batchId) return;
      setScanValue('');
      const match = steps.find(
        (step) => step.sku.toLowerCase() === barcode.toLowerCase(),
      );
      if (!match) {
        setScanError('SKU not found in pick sequence.');
        setHighlightSlot(null);
        setLastSku(barcode);
        return;
      }
      setScanError(null);
      setLastSku(match.sku);
      setHighlightSlot(match.slotIndex);
      playCorrectSlotBeep();
    },
    [batchId, steps],
  );

  useHardwareScanner({
    enabled: batchId != null,
    onScan: (barcode) => handleScan(barcode),
  });

  return (
    <div className="p-4 sm:p-6" data-testid="cluster-picker-view">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text">Cluster pick</h1>
        <p className="mt-1 text-sm text-text-muted">
          Multi-order tote grid — scan SKU to highlight the target slot from the pick sequence.
        </p>
      </div>

      <Card className="mb-6">
        <CardHeader title="Batch" description="Load the directed cluster pick sequence for a batch." />
        <form
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            const id = batchIdInput.trim();
            if (id) setBatchId(id);
          }}
        >
          <Input
            label="Batch ID"
            value={batchIdInput}
            onChange={(e) => setBatchIdInput(e.target.value)}
            placeholder="UUID of picking batch…"
            autoComplete="off"
            data-testid="cluster-pick-batch-input"
          />
          <Button type="submit" className="min-h-11 sm:mb-0.5" disabled={!batchIdInput.trim()}>
            Load sequence
          </Button>
        </form>
      </Card>

      <Card className="mb-6">
        <CardHeader
          title="Barcode scan"
          description={batchId ? `Batch ${batchId.slice(0, 8)}…` : 'Load a batch to begin scanning'}
        />
        <form
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            handleScan(scanValue);
          }}
        >
          <Input
            label="Scan SKU"
            value={scanValue}
            onChange={(e) => setScanValue(e.target.value)}
            placeholder="Scan or type SKU…"
            autoComplete="off"
            data-testid="cluster-pick-scan-input"
            disabled={!batchId}
          />
          <Button
            type="submit"
            className="min-h-11 sm:mb-0.5"
            disabled={!batchId || !scanValue.trim()}
          >
            <ScanLine className="h-4 w-4" />
            Submit
          </Button>
        </form>
        {lastSku && highlightSlot != null && (
          <p className="mt-3 text-sm text-text">
            Last scan: <span className="font-mono font-semibold">{lastSku}</span> → slot{' '}
            <span className="font-mono font-semibold">{highlightSlot}</span>
          </p>
        )}
        {scanError && <p className="mt-2 text-sm text-danger">{scanError}</p>}
      </Card>

      {isLoading ? (
        <p className="text-sm text-text-muted">Loading cluster slots…</p>
      ) : (
        <div
          className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
          data-testid="cluster-slot-grid"
        >
          {Array.from({ length: 12 }, (_, i) => i + 1).map((slotIndex) => {
            const slot = slots.get(slotIndex);
            const isTarget = highlightSlot === slotIndex;
            const colorClass = slot ? slotColor(slotIndex) : 'bg-surface-overlay border-border text-text-muted';
            return (
              <div
                key={slotIndex}
                data-testid={`cluster-slot-${slotIndex}`}
                className={cn(
                  'flex min-h-[7rem] flex-col rounded-lg border-2 p-3 transition-all',
                  colorClass,
                  isTarget && 'ring-4 ring-accent ring-offset-2 ring-offset-surface scale-[1.02]',
                  !slot && 'opacity-60',
                )}
              >
                <span className="text-xs font-bold uppercase tracking-wide">Slot {slotIndex}</span>
                {slot ? (
                  <>
                    <span className="mt-2 font-mono text-sm font-bold leading-tight">
                      {slot.toteBarcode}
                    </span>
                    <span className="mt-1 truncate text-xs opacity-80">
                      {slot.skus.slice(0, 2).join(', ')}
                      {slot.skus.length > 2 ? ` +${slot.skus.length - 2}` : ''}
                    </span>
                  </>
                ) : (
                  <span className="mt-2 text-xs">Empty</span>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

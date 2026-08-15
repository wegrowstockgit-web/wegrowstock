import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Check, Delete } from 'lucide-react';
import { useMemo, useState } from 'react';
import { apiClient } from '@/api/client';
import type { CycleCountDetail, CycleCountLineView } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { NetworkStatusBadge } from '@/components/layout/NetworkStatusBadge';
import { enqueueScanMutation } from '@/offline/mutationQueue';
import { createScanEventPayload } from '@/offline/scanEvent';
import { cn } from '@/lib/utils';

interface CycleCountScannerProps {
  cycleCountId: string;
  onBack: () => void;
  onComplete?: () => void;
}

function nextPendingLine(detail: CycleCountDetail | undefined): CycleCountLineView | null {
  if (!detail?.lines?.length) return null;
  return (
    detail.lines.find(
      (l) =>
        l.varianceStatus === 'PENDING' ||
        l.varianceStatus === 'RECOUNT_REQUESTED',
    ) ?? null
  );
}

function formatQty(value: number | string | null | undefined): string {
  if (value == null) return '0';
  const n = Number(value);
  return Number.isFinite(n) ? String(n) : String(value);
}

/**
 * Surface B — floor cycle-count keypad.
 * Blind mode hides expected_qty to prevent confirmation bias.
 */
export function CycleCountScanner({ cycleCountId, onBack, onComplete }: CycleCountScannerProps) {
  const queryClient = useQueryClient();
  const [digits, setDigits] = useState('');
  const [flash, setFlash] = useState<'ok' | 'err' | 'pending' | null>(null);
  const [lastStatus, setLastStatus] = useState<string | null>(null);
  const [scannedCount, setScannedCount] = useState(0);

  const { data: detail, isLoading, error } = useQuery({
    queryKey: ['cycle-counts', cycleCountId],
    networkMode: 'offlineFirst',
    queryFn: async () => {
      const open = await apiClient.post<CycleCountDetail>(`/api/v1/cycle-counts/${cycleCountId}/open`);
      return open.data;
    },
    retry: false,
  });

  const line = useMemo(() => nextPendingLine(detail), [detail]);
  const blind = detail?.blindCycleCounts ?? true;

  const submit = useMutation({
    networkMode: 'offlineFirst',
    mutationFn: async (countedQty: number) => {
      if (!line) throw new Error('No pending line');
      const body = { countedQty };
      const url = `/api/v1/cycle-counts/${cycleCountId}/lines/${line.id}/submit`;
      const scanEvent = createScanEventPayload(line.sku ?? line.id);
      if (!navigator.onLine) {
        await enqueueScanMutation(scanEvent, {
          method: 'POST',
          url,
          body,
        });
        return {
          ...line,
          countedQty,
          varianceStatus: 'COUNTED_OFFLINE',
        } as CycleCountLineView;
      }
      const res = await apiClient.post<CycleCountLineView>(url, body, {
        headers: { 'Idempotency-Key': scanEvent.idempotencyKey },
      });
      return res.data;
    },
    onMutate: async (countedQty) => {
      if (!line) return;
      setScannedCount((c) => c + 1);
      if (!navigator.onLine) {
        setFlash('pending');
        window.setTimeout(() => setFlash(null), 600);
      }
      await queryClient.cancelQueries({ queryKey: ['cycle-counts', cycleCountId] });
      const previous = queryClient.getQueryData<CycleCountDetail>(['cycle-counts', cycleCountId]);
      queryClient.setQueryData<CycleCountDetail>(['cycle-counts', cycleCountId], (old) => {
        if (!old) return old;
        return {
          ...old,
          lines: old.lines.map((l) =>
            l.id === line.id
              ? { ...l, countedQty, varianceStatus: 'COUNTED_OFFLINE' }
              : l,
          ),
        };
      });
      return { previous };
    },
    onSuccess: async (result) => {
      setDigits('');
      setLastStatus(result.varianceStatus);
      if (navigator.onLine) {
        setFlash('ok');
        window.setTimeout(() => setFlash(null), 600);
      }
      if (!navigator.onLine) {
        const current = queryClient.getQueryData<CycleCountDetail>(['cycle-counts', cycleCountId]);
        if (!nextPendingLine(current)) {
          onComplete?.();
        }
        return;
      }
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts', cycleCountId] });
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts', 'priority-audits'] });
      await queryClient.invalidateQueries({ queryKey: ['cycle-counts', 'pending-variances'] });
      const refreshed = await apiClient.get<CycleCountDetail>(`/api/v1/cycle-counts/${cycleCountId}`);
      if (!nextPendingLine(refreshed.data)) {
        onComplete?.();
      }
    },
    onError: (_err, _vars, ctx) => {
      setFlash('err');
      setScannedCount((c) => Math.max(0, c - 1));
      if (ctx?.previous) {
        queryClient.setQueryData(['cycle-counts', cycleCountId], ctx.previous);
      }
      window.setTimeout(() => setFlash(null), 700);
    },
  });

  const append = (d: string) => {
    setDigits((prev) => {
      if (d === '.' && prev.includes('.')) return prev;
      if (prev.length >= 10) return prev;
      return prev + d;
    });
  };

  const backspace = () => setDigits((prev) => prev.slice(0, -1));

  const submitValue = (raw: string) => {
    const n = Number(raw);
    if (!Number.isFinite(n) || n < 0) return;
    submit.mutate(n);
  };

  const confirmMatch = () => {
    if (!line) return;
    submitValue(formatQty(line.expectedQty));
  };

  if (isLoading) {
    return (
      <div className="flex min-h-[320px] items-center justify-center text-sm text-text-muted" data-testid="cycle-count-loading">
        Opening count…
      </div>
    );
  }

  if (error || !detail) {
    return (
      <div className="space-y-4 p-4" data-testid="cycle-count-error">
        <p className="text-sm text-danger">Could not open this cycle count.</p>
        <Button variant="secondary" onClick={onBack}>
          Back
        </Button>
      </div>
    );
  }

  if (!line) {
    return (
      <div className="space-y-4 p-4 text-center" data-testid="cycle-count-complete">
        <Check className="mx-auto h-10 w-10 text-accent" />
        <h2 className="text-xl font-semibold text-text">Bin count complete</h2>
        <p className="text-sm text-text-muted">{detail.locationPath}</p>
        {lastStatus && (
          <p className="text-xs text-text-muted">Last line: {lastStatus.replaceAll('_', ' ')}</p>
        )}
        <Button onClick={onBack} data-testid="cycle-count-done">
          Done
        </Button>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'mx-auto flex w-full max-w-md flex-col gap-5 px-3 py-4 transition-transform duration-150',
        flash === 'ok' && 'scale-[1.01]',
        flash === 'err' && 'animate-pulse',
        flash === 'pending' && 'bg-warning/10',
      )}
      data-testid="cycle-count-scanner"
      data-blind={blind ? 'true' : 'false'}
      data-flash={flash ?? undefined}
    >
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={onBack} aria-label="Back" data-testid="cycle-count-back">
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div className="min-w-0 flex-1">
          <p className="truncate text-xs uppercase tracking-wide text-text-muted">Cycle count</p>
          <p className="truncate font-mono text-sm text-text" data-testid="cycle-count-location">
            {detail.locationPath}
          </p>
          {scannedCount > 0 && (
            <p className="text-xs font-medium text-accent" data-testid="cycle-count-scanned">
              Scanned {scannedCount}
            </p>
          )}
          {flash === 'pending' && (
            <p
              className="text-xs font-semibold uppercase tracking-wide text-warning"
              data-testid="cycle-count-pending-sync"
            >
              Pending Sync
            </p>
          )}
        </div>
        <NetworkStatusBadge />
      </div>

      <div className="text-center">
        <p className="text-sm text-text-muted">SKU</p>
        <p className="mt-1 font-mono text-2xl font-bold tracking-tight text-text" data-testid="cycle-count-sku">
          {line.sku}
        </p>
        {blind ? (
          <p className="mt-3 text-base text-text" data-testid="cycle-count-prompt">
            Enter total physical quantity for SKU {line.sku}
          </p>
        ) : (
          <p className="mt-3 text-sm text-text-muted" data-testid="cycle-count-expected">
            Expected qty: <span className="font-mono font-semibold text-text">{formatQty(line.expectedQty)}</span>
          </p>
        )}
      </div>

      <div
        className="rounded-lg border border-border bg-surface-raised px-4 py-5 text-center"
        data-testid="cycle-count-display"
      >
        <span className="font-mono text-5xl font-semibold tabular-nums text-text">
          {digits || '0'}
        </span>
      </div>

      {!blind && (
        <Button
          size="lg"
          className="h-14 w-full text-base"
          onClick={confirmMatch}
          disabled={submit.isPending}
          data-testid="cycle-count-confirm-match"
        >
          Confirm Match
        </Button>
      )}

      <div className="grid grid-cols-3 gap-2" data-testid="cycle-count-keypad">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', '⌫'].map((key) => (
          <button
            key={key}
            type="button"
            className={cn(
              'h-16 rounded-md border border-border bg-surface-raised text-2xl font-semibold text-text',
              'active:scale-[0.98] active:bg-surface-overlay transition-transform',
              key === '⌫' && 'text-lg',
            )}
            onClick={() => (key === '⌫' ? backspace() : append(key))}
            data-testid={`cycle-count-key-${key === '⌫' ? 'backspace' : key === '.' ? 'dot' : key}`}
          >
            {key === '⌫' ? <Delete className="mx-auto h-6 w-6" /> : key}
          </button>
        ))}
      </div>

      <Button
        size="lg"
        className="h-14 w-full text-base"
        loading={submit.isPending}
        disabled={!digits || submit.isPending}
        onClick={() => submitValue(digits)}
        data-testid="cycle-count-submit"
      >
        Submit count
      </Button>

      {lastStatus && (
        <p className="text-center text-xs text-text-muted" data-testid="cycle-count-last-status">
          Last result: {lastStatus.replaceAll('_', ' ')}
        </p>
      )}
    </div>
  );
}

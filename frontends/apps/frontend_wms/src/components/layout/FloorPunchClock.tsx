import { useEffect, useId, useState } from 'react';
import { createPortal } from 'react-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock, X } from 'lucide-react';
import { apiClient } from '@/api/client';
import { refetchIntervalWhileAuthenticated } from '@/lib/queryClient';
import { HardwareManualFallback } from '@/components/hardware/HardwareManualFallback';
import { Button } from '@/components/ui/Button';
import { getHardwareCapabilities } from '@/lib/hardwareCapabilities';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';

interface LaborStatus {
  shiftId: string | null;
  warehouseId: string | null;
  clockIn: string | null;
  clockOut: string | null;
  currentActivity: string | null;
  active: boolean;
}

const ACTIVITIES = [
  'PICKING',
  'RECEIVING',
  'PUTAWAY',
  'CYCLE_COUNT',
  'BREAK',
  'MEETING',
  'INDIRECT_CLEANING',
] as const;

export function FloorPunchClock({ warehouseSized }: { warehouseSized?: boolean }) {
  const queryClient = useQueryClient();
  const warehouseId = useActiveWarehouseStore((s) => s.warehouseId);
  const titleId = useId();
  const [sheetOpen, setSheetOpen] = useState(false);
  const isMobile = useMediaQuery('(max-width: 767px)');

  const { data: status } = useQuery({
    queryKey: ['labor', 'me'],
    queryFn: async () => (await apiClient.get<LaborStatus>('/api/v1/labor/me')).data,
    refetchInterval: refetchIntervalWhileAuthenticated(60_000),
  });

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['labor'] });

  const clockIn = useMutation({
    mutationFn: async () =>
      apiClient.post('/api/v1/labor/clock-in', { warehouseId: warehouseId || null }),
    onSuccess: invalidate,
  });
  const clockOut = useMutation({
    mutationFn: async () => apiClient.post('/api/v1/labor/clock-out'),
    onSuccess: () => {
      invalidate();
      setSheetOpen(false);
    },
  });
  const switchActivity = useMutation({
    mutationFn: async (activityType: string) =>
      apiClient.post('/api/v1/labor/switch-activity', { activityType }),
    onSuccess: invalidate,
  });

  const active = Boolean(status?.active);
  const activity = status?.currentActivity ?? 'OFF';
  const { isSupported, isBluetoothSupported, isSerialSupported } = getHardwareCapabilities();

  useEffect(() => {
    if (!sheetOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSheetOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [sheetOpen]);

  const activityControls = (
    <>
      <span className="text-xs font-semibold text-text" data-testid="labor-activity-label">
        On Clock: {activity}
      </span>
      <select
        className="h-8 max-w-[8rem] rounded border border-border bg-surface px-1 text-xs"
        data-testid="labor-switch-activity"
        value={ACTIVITIES.includes(activity as (typeof ACTIVITIES)[number]) ? activity : 'PICKING'}
        onChange={(e) => switchActivity.mutate(e.target.value)}
      >
        {ACTIVITIES.map((a) => (
          <option key={a} value={a}>
            {a}
          </option>
        ))}
      </select>
      <Button
        size="sm"
        variant="ghost"
        data-testid="labor-clock-out"
        loading={clockOut.isPending}
        onClick={() => clockOut.mutate()}
      >
        Clock Out
      </Button>
    </>
  );

  const clockInButton = (
    <Button
      size="sm"
      variant="secondary"
      data-testid="labor-clock-in"
      loading={clockIn.isPending}
      onClick={() => clockIn.mutate()}
    >
      Clock In
    </Button>
  );

  return (
    <div
      className={cn(
        'inline-flex items-center gap-1 rounded-md border border-border bg-surface-raised px-1.5 py-1 md:px-2',
        warehouseSized && 'min-h-11',
      )}
      data-testid="floor-punch-clock"
    >
      {isMobile ? (
        <button
          type="button"
          className={cn(
            'relative inline-flex min-h-11 min-w-11 items-center justify-center rounded-md',
            'text-text hover:bg-surface-overlay',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
            active && 'text-accent',
          )}
          aria-label={active ? `Labor clock, ${activity}` : 'Labor clock'}
          aria-expanded={sheetOpen}
          aria-controls="labor-clock-sheet"
          data-testid="labor-clock-menu-toggle"
          onClick={() => setSheetOpen(true)}
        >
          <Clock className="h-5 w-5" aria-hidden />
          {active && (
            <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-accent" aria-hidden />
          )}
        </button>
      ) : (
        <div className="inline-flex items-center gap-1">
          <Clock className="h-4 w-4 text-text-muted" aria-hidden />
          {!active ? clockInButton : activityControls}
          <HardwareManualFallback
            isSupported={isSupported}
            mode="weight"
            bluetoothSupported={isBluetoothSupported}
            serialSupported={isSerialSupported}
            className="w-28"
            onManualSubmit={(value) => {
              window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: value } }));
            }}
          />
        </div>
      )}

      {isMobile &&
        sheetOpen &&
        createPortal(
          <div
            className="fixed inset-0 z-[80] flex items-end justify-center bg-black/50 p-3 md:hidden"
            data-testid="labor-clock-sheet-backdrop"
            onClick={() => setSheetOpen(false)}
          >
            <div
              id="labor-clock-sheet"
              role="dialog"
              aria-modal="true"
              aria-labelledby={titleId}
              data-testid="labor-clock-sheet"
              className="w-full max-w-md rounded-t-2xl border border-border bg-surface-raised p-4 shadow-elevated"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 id={titleId} className="text-base font-semibold text-text">
                  Labor clock
                </h2>
                <button
                  type="button"
                  className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-md text-text-muted hover:bg-surface-overlay"
                  aria-label="Close labor clock"
                  onClick={() => setSheetOpen(false)}
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
              <div className="flex flex-col gap-3">
                {!active ? clockInButton : <div className="flex flex-wrap items-center gap-2">{activityControls}</div>}
              </div>
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
}

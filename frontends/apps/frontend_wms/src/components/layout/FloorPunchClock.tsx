import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock } from 'lucide-react';
import { apiClient } from '@/api/client';
import { refetchIntervalWhileAuthenticated } from '@/lib/queryClient';
import { HardwareManualFallback } from '@/components/hardware/HardwareManualFallback';
import { Button } from '@/components/ui/Button';
import { getHardwareCapabilities } from '@/lib/hardwareCapabilities';
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
  'PUTAWAY',
  'CYCLE_COUNT',
  'BREAK',
  'MEETING',
  'INDIRECT_CLEANING',
] as const;

export function FloorPunchClock({ warehouseSized }: { warehouseSized?: boolean }) {
  const queryClient = useQueryClient();
  const warehouseId = useActiveWarehouseStore((s) => s.warehouseId);

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
    onSuccess: invalidate,
  });
  const switchActivity = useMutation({
    mutationFn: async (activityType: string) =>
      apiClient.post('/api/v1/labor/switch-activity', { activityType }),
    onSuccess: invalidate,
  });

  const active = Boolean(status?.active);
  const activity = status?.currentActivity ?? 'OFF';
  const { isSupported, isBluetoothSupported, isSerialSupported } = getHardwareCapabilities();

  return (
    <div
      className={cn(
        'inline-flex items-center gap-1 rounded-md border border-border bg-surface-raised px-2 py-1',
        warehouseSized && 'min-h-11',
      )}
      data-testid="floor-punch-clock"
    >
      <Clock className="h-4 w-4 text-text-muted" aria-hidden />
      {!active ? (
        <Button
          size="sm"
          variant="secondary"
          data-testid="labor-clock-in"
          loading={clockIn.isPending}
          onClick={() => clockIn.mutate()}
        >
          Clock In
        </Button>
      ) : (
        <>
          <span className="text-xs font-semibold text-text" data-testid="labor-activity-label">
            On Clock: {activity}
          </span>
          <select
            className="h-8 max-w-[8rem] rounded border border-border bg-surface px-1 text-xs"
            data-testid="labor-switch-activity"
            value={activity}
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
      )}
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
  );
}

import { apiClient } from '@/api/client';
import { useUiActionTrackerStore } from '@/stores/uiActionTrackerStore';

export type LaborScanActivity = 'PICKING' | 'RECEIVING';

interface LaborStatus {
  currentActivity: string | null;
  active: boolean;
}

const PICKING_PREFIX = /^(LPN-|TOTE-|WAVE-|TOT-|PAL-|MIB-)/i;
const RECEIVING_PREFIX = /^(PO-|ASN-|RCV-|DOCK-|REC-|RECEIVING[-_]?)/i;

let lastAppliedKey = '';
let lastAppliedAt = 0;
let inFlight: Promise<void> | null = null;

export function inferLaborActivityFromScan(
  barcode: string,
  context?: { mode?: string | null; pathname?: string | null },
): LaborScanActivity | null {
  const raw = barcode.trim();
  if (!raw) return null;
  const upper = raw.toUpperCase();
  const mode = (context?.mode ?? '').toLowerCase();
  const path = (context?.pathname ?? '').toLowerCase();

  if (
    mode === 'receive' ||
    path.includes('/inbound') ||
    RECEIVING_PREFIX.test(upper) ||
    upper.includes('DOCK')
  ) {
    return 'RECEIVING';
  }

  if (
    mode === 'pick' ||
    mode === 'lpn' ||
    mode === 'pallet' ||
    path.includes('/fulfillment') ||
    PICKING_PREFIX.test(upper)
  ) {
    return 'PICKING';
  }

  return null;
}

export async function applyLaborFromScan(options: {
  barcode: string;
  warehouseId?: string | null;
  mode?: string | null;
  pathname?: string | null;
}): Promise<LaborScanActivity | null> {
  const activity = inferLaborActivityFromScan(options.barcode, {
    mode: options.mode,
    pathname: options.pathname,
  });
  if (!activity) return null;

  const now = Date.now();
  const key = `${activity}`;
  if (lastAppliedKey === key && now - lastAppliedAt < 2_500) {
    return activity;
  }

  if (inFlight) {
    await inFlight;
    if (lastAppliedKey === key) return activity;
  }

  inFlight = (async () => {
    try {
      const me = (await apiClient.get<LaborStatus>('/api/v1/labor/me')).data;
      if (me.active && (me.currentActivity ?? '').toUpperCase() === activity) {
        lastAppliedKey = key;
        lastAppliedAt = Date.now();
        return;
      }

      if (!me.active) {
        await apiClient.post('/api/v1/labor/clock-in', {
          warehouseId: options.warehouseId || null,
        });
      }

      const current = me.active ? (me.currentActivity ?? '').toUpperCase() : 'PICKING';
      if (current !== activity) {
        await apiClient.post('/api/v1/labor/switch-activity', { activityType: activity });
      }

      lastAppliedKey = key;
      lastAppliedAt = Date.now();
      useUiActionTrackerStore.getState().trackAction({
        actionType: 'LABOR_AUTO_SWITCH',
        elementLabel: `Auto labor ${activity} · ${options.barcode.trim().slice(0, 48)}`,
      });
    } catch {
      // Floor velocity: never block the scan path on labor API failures.
    }
  })();

  await inFlight;
  inFlight = null;
  return activity;
}

/** Test helper */
export function resetLaborScanAutomationForTests(): void {
  lastAppliedKey = '';
  lastAppliedAt = 0;
  inFlight = null;
}

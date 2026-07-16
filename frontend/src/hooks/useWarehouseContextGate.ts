import { useEffect } from 'react';
import { apiClient } from '@/api/client';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useSessionStore } from '@/stores/session';
import type { Warehouse } from '@/api/types';

export interface NetworkContextHint {
  ssid?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

interface ResolveResponse {
  matched: boolean;
  locked?: boolean;
  warehouseId?: string;
  matchType?: 'WIFI_SSID' | 'GEOFENCE';
  label?: string;
}

const HINT_STORAGE_KEY = 'invsys-network-hint';
const HINT_EVENT = 'invsys:network-context';

/**
 * Read MDM / Zebra / Honeywell injected network hints.
 * Browsers cannot read Wi-Fi SSID without privileged APIs — devices and e2e
 * inject via custom event or localStorage.
 */
export function readNetworkContextHint(): NetworkContextHint {
  try {
    const raw = localStorage.getItem(HINT_STORAGE_KEY);
    if (raw) {
      return JSON.parse(raw) as NetworkContextHint;
    }
  } catch {
    // ignore
  }
  return {};
}

export function writeNetworkContextHint(hint: NetworkContextHint): void {
  localStorage.setItem(HINT_STORAGE_KEY, JSON.stringify(hint));
  window.dispatchEvent(new CustomEvent(HINT_EVENT, { detail: hint }));
}

async function probeGeolocation(): Promise<{ latitude: number; longitude: number } | null> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return null;
  }
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => resolve(null), 2500);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        window.clearTimeout(timer);
        resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
      },
      () => {
        window.clearTimeout(timer);
        resolve(null);
      },
      { enableHighAccuracy: false, maximumAge: 60_000, timeout: 2000 }
    );
  });
}

/**
 * Automated warehouse context gate: SSID / geofence resolve on boot.
 * Skipped when JWT already locks to a single warehouse.
 */
export function useWarehouseContextGate(
  warehouses: Warehouse[],
  jwtTerminalLocked: boolean
): void {
  const authenticated = useSessionStore((s) => s.isAuthenticated());
  const lockFromHardware = useActiveWarehouseStore((s) => s.lockFromHardware);
  const clearHardwareLock = useActiveWarehouseStore((s) => s.clearHardwareLock);

  useEffect(() => {
    if (!authenticated || warehouses.length === 0 || jwtTerminalLocked) {
      return;
    }

    let cancelled = false;

    const applyMatch = (res: ResolveResponse) => {
      if (cancelled || !res.matched || !res.warehouseId) return;
      const warehouse = warehouses.find((w) => w.id === res.warehouseId);
      if (!warehouse || !res.matchType) return;
      lockFromHardware(warehouse, res.matchType);
    };

    const resolve = async (hint: NetworkContextHint) => {
      const geo =
        hint.latitude != null && hint.longitude != null
          ? { latitude: hint.latitude, longitude: hint.longitude }
          : await probeGeolocation();
      const body = {
        ssid: hint.ssid ?? undefined,
        latitude: geo?.latitude ?? hint.latitude ?? undefined,
        longitude: geo?.longitude ?? hint.longitude ?? undefined,
      };
      if (!body.ssid && body.latitude == null) {
        return;
      }
      try {
        const response = await apiClient.post<ResolveResponse>(
          '/api/v1/terminals/resolve-context',
          body
        );
        applyMatch(response.data);
      } catch {
        clearHardwareLock();
      }
    };

    void resolve(readNetworkContextHint());

    const onHint = (event: Event) => {
      const detail = (event as CustomEvent<NetworkContextHint>).detail ?? {};
      void resolve({ ...readNetworkContextHint(), ...detail });
    };
    window.addEventListener(HINT_EVENT, onHint);
    return () => {
      cancelled = true;
      window.removeEventListener(HINT_EVENT, onHint);
    };
  }, [
    authenticated,
    warehouses,
    jwtTerminalLocked,
    lockFromHardware,
    clearHardwareLock,
  ]);
}

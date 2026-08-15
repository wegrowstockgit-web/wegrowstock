import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { useSessionRoles, useSessionStore } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useOfflineStore } from '@/stores/offlineStore';
import { useNetworkSyncStore } from '@/stores/networkSyncStore';
import { useScannerLockStore } from '@/stores/scannerLockStore';
import { getFreshSupportNetworkError } from './supportNetworkTelemetry';
import { useUiActionTrackerStore, type UiActionBreadcrumb } from '@/stores/uiActionTrackerStore';

export type PageStateSnapshot = {
  pathname: string;
  search: string;
  /** Full path including query (convenience for chat payloads). */
  routePath: string;
  /** ID of currently selected / drawer-opened entity (order, item, user). */
  selectedEntityId: string | null;
  /** @deprecated alias of selectedEntityId */
  selectedEntity: string | null;
  userRoles: string[];
  activeWarehouseId: string | null;
  activeWarehouseName: string | null;
  /** Why the warehouse context is locked (JWT single / hardware). */
  lockReason: string | null;
  activeFilter: string | null;
  activeTab: string | null;
  /** Network status from the sync store. */
  networkPhase: 'offline' | 'syncing' | 'online';
  /** @deprecated alias of networkPhase */
  networkState: 'offline' | 'syncing' | 'online';
  quarantineCount: number;
  /** Alias used by Co-Pilot telemetry injection. */
  quarantinedMutationsCount: number;
  /** Scanner PIN / idle lock gate. */
  isDeviceLocked: boolean;
  /** W3C / request trace id from the last failed API call (or session request id). */
  trace_id: string | null;
  lastHttpErrorStatus: number | null;
  lastHttpErrorMessage: string | null;
  /** Last 5 UI interactions for temporal coaching. */
  recentBreadcrumbs: UiActionBreadcrumb[];
};

function readSearchParam(search: string, key: string): string | null {
  if (!search) return null;
  try {
    return new URLSearchParams(search.startsWith('?') ? search : `?${search}`).get(key);
  } catch {
    return null;
  }
}

/**
 * Real-time operational context for the support copilot payload.
 * Derived from router location + Zustand stores (session, warehouse, offline, network).
 */
export function usePageStateSnapshot(): PageStateSnapshot {
  const location = useLocation();
  const userRoles = useSessionRoles();
  const warehouseId = useActiveWarehouseStore((s) => s.warehouseId);
  const warehouseName = useActiveWarehouseStore((s) => s.warehouse?.name ?? null);
  const lockReason = useActiveWarehouseStore((s) => s.lockReason);
  const quarantineCount = useOfflineStore((s) => s.quarantinedMutations.length);
  const isDeviceLocked = useScannerLockStore((s) => s.isLocked);
  const lastRequestId = useSessionStore((s) => s.lastRequestId);
  const online = useNetworkSyncStore((s) => s.online);
  const syncing = useNetworkSyncStore((s) => s.syncing);
  const pendingCount = useNetworkSyncStore((s) => s.pendingCount);
  const getRecentBreadcrumbs = useUiActionTrackerStore((s) => s.getRecentBreadcrumbs);
  const uiActions = useUiActionTrackerStore((s) => s.actions);

  return useMemo(() => {
    const tab = readSearchParam(location.search, 'tab');
    const status = readSearchParam(location.search, 'status');
    const q = readSearchParam(location.search, 'q') ?? readSearchParam(location.search, 'search');
    const selectedEntityId =
      readSearchParam(location.search, 'id')
      ?? readSearchParam(location.search, 'orderId')
      ?? readSearchParam(location.search, 'poId')
      ?? null;

    const filterParts = [status && `status=${status}`, q && `q=${q}`, tab && `tab=${tab}`].filter(
      Boolean,
    ) as string[];

    const networkPhase: PageStateSnapshot['networkPhase'] = !online
      ? 'offline'
      : syncing || pendingCount > 0
        ? 'syncing'
        : 'online';

    const networkError = getFreshSupportNetworkError();
    const trace_id = networkError?.traceId ?? lastRequestId;
    const recentBreadcrumbs = getRecentBreadcrumbs(5);

    return {
      pathname: location.pathname,
      search: location.search,
      routePath: `${location.pathname}${location.search}`,
      selectedEntityId,
      selectedEntity: selectedEntityId,
      userRoles: [...userRoles],
      activeWarehouseId: warehouseId,
      activeWarehouseName: warehouseName,
      lockReason,
      activeFilter: filterParts.length > 0 ? filterParts.join('&') : null,
      activeTab: tab,
      networkPhase,
      networkState: networkPhase,
      quarantineCount,
      quarantinedMutationsCount: quarantineCount,
      isDeviceLocked,
      trace_id,
      lastHttpErrorStatus: networkError?.status ?? null,
      lastHttpErrorMessage: networkError?.message ?? null,
      recentBreadcrumbs,
    };
  }, [
    location.pathname,
    location.search,
    userRoles,
    warehouseId,
    warehouseName,
    lockReason,
    quarantineCount,
    isDeviceLocked,
    online,
    syncing,
    pendingCount,
    lastRequestId,
    uiActions,
    getRecentBreadcrumbs,
  ]);
}

import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { useSessionRoles } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useOfflineStore } from '@/stores/offlineStore';
import { useNetworkSyncStore } from '@/stores/networkSyncStore';

export type PageStateSnapshot = {
  routePath: string;
  pathname: string;
  search: string;
  userRoles: string[];
  activeWarehouseId: string | null;
  activeWarehouseName: string | null;
  activeFilter: string | null;
  activeTab: string | null;
  networkState: 'offline' | 'syncing' | 'online';
  quarantineCount: number;
  selectedEntity: string | null;
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
  const quarantineCount = useOfflineStore((s) => s.quarantinedMutations.length);
  const online = useNetworkSyncStore((s) => s.online);
  const syncing = useNetworkSyncStore((s) => s.syncing);
  const pendingCount = useNetworkSyncStore((s) => s.pendingCount);

  return useMemo(() => {
    const tab = readSearchParam(location.search, 'tab');
    const status = readSearchParam(location.search, 'status');
    const q = readSearchParam(location.search, 'q') ?? readSearchParam(location.search, 'search');
    const selected =
      readSearchParam(location.search, 'id')
      ?? readSearchParam(location.search, 'orderId')
      ?? readSearchParam(location.search, 'poId')
      ?? null;

    const filterParts = [status && `status=${status}`, q && `q=${q}`, tab && `tab=${tab}`].filter(
      Boolean,
    ) as string[];

    const networkState: PageStateSnapshot['networkState'] = !online
      ? 'offline'
      : syncing || pendingCount > 0
        ? 'syncing'
        : 'online';

    return {
      routePath: `${location.pathname}${location.search}`,
      pathname: location.pathname,
      search: location.search,
      userRoles: [...userRoles],
      activeWarehouseId: warehouseId,
      activeWarehouseName: warehouseName,
      activeFilter: filterParts.length > 0 ? filterParts.join('&') : null,
      activeTab: tab,
      networkState,
      quarantineCount,
      selectedEntity: selected,
    };
  }, [
    location.pathname,
    location.search,
    userRoles,
    warehouseId,
    warehouseName,
    quarantineCount,
    online,
    syncing,
    pendingCount,
  ]);
}

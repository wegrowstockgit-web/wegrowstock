import { create } from 'zustand';
import { apiClient } from '@/api/client';
import { useSessionStore } from '@/stores/session';
import type { Warehouse } from '@/api/types';

const FLOOR_ROLES = new Set(['PICKER']);

interface WarehouseStoreState {
  allowed: Warehouse[];
  loading: boolean;
  error: string | null;
  /** True when floor roles / single-facility JWT must not change the switcher. */
  switcherDisabled: boolean;
  fetchAllowed: () => Promise<Warehouse[]>;
  clear: () => void;
}

function computeSwitcherDisabled(allowedCount: number): boolean {
  const user = useSessionStore.getState().user;
  const roles = user?.roles ?? [];
  const warehouseIds = user?.warehouseIds ?? [];
  const floorRestricted = roles.some((r) => FLOOR_ROLES.has(r)) && !roles.some((r) =>
    r === 'OWNER' || r === 'ADMIN' || r === 'WAREHOUSE_MANAGER'
  );
  return floorRestricted || warehouseIds.length === 1 || allowedCount === 1;
}

/**
 * Allowed warehouses for the active session (LBAC).
 * Populated from {@code GET /api/v1/locations/warehouses/allowed}.
 * Selection state remains in {@code activeWarehouse}; this store owns the allow-list.
 */
export const useWarehouseStore = create<WarehouseStoreState>((set) => ({
  allowed: [],
  loading: false,
  error: null,
  switcherDisabled: false,

  fetchAllowed: async () => {
    set({ loading: true, error: null });
    try {
      const res = await apiClient.get<Warehouse[]>('/api/v1/locations/warehouses/allowed');
      const allowed = res.data ?? [];
      set({
        allowed,
        loading: false,
        switcherDisabled: computeSwitcherDisabled(allowed.length),
      });
      return allowed;
    } catch (err) {
      set({
        loading: false,
        error: err instanceof Error ? err.message : 'Failed to load warehouses',
        allowed: [],
        switcherDisabled: true,
      });
      return [];
    }
  },

  clear: () =>
    set({
      allowed: [],
      loading: false,
      error: null,
      switcherDisabled: false,
    }),
}));

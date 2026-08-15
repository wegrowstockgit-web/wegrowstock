import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Warehouse } from '@/api/types';

export type WarehouseLockReason = 'JWT_SINGLE' | 'HARDWARE_SSID' | 'HARDWARE_GEOFENCE' | null;

interface ActiveWarehouseState {
  warehouseId: string | null;
  warehouse: Warehouse | null;
  /** True when dropdown must stay hidden (JWT single or hardware match). */
  contextLocked: boolean;
  lockReason: WarehouseLockReason;
  setWarehouse: (
    warehouse: Warehouse,
    options?: { force?: boolean; lockReason?: WarehouseLockReason }
  ) => void;
  lockFromHardware: (warehouse: Warehouse, matchType: 'WIFI_SSID' | 'GEOFENCE') => void;
  lockFromJwtSingle: (warehouse: Warehouse) => void;
  clearHardwareLock: () => void;
  clearWarehouse: () => void;
}

export const useActiveWarehouseStore = create<ActiveWarehouseState>()(
  persist(
    (set, get) => ({
      warehouseId: null,
      warehouse: null,
      contextLocked: false,
      lockReason: null,

      setWarehouse: (warehouse, options) => {
        const state = get();
        const force = options?.force === true;
        if (state.contextLocked && !force && state.warehouseId && state.warehouseId !== warehouse.id) {
          // Hard gate: ignore human/persisted overrides while locked.
          return;
        }
        set({
          warehouseId: warehouse.id,
          warehouse,
          contextLocked: options?.lockReason != null ? true : state.contextLocked,
          lockReason: options?.lockReason ?? state.lockReason,
        });
      },

      lockFromHardware: (warehouse, matchType) =>
        set({
          warehouseId: warehouse.id,
          warehouse,
          contextLocked: true,
          lockReason: matchType === 'WIFI_SSID' ? 'HARDWARE_SSID' : 'HARDWARE_GEOFENCE',
        }),

      lockFromJwtSingle: (warehouse) =>
        set({
          warehouseId: warehouse.id,
          warehouse,
          contextLocked: true,
          lockReason: 'JWT_SINGLE',
        }),

      clearHardwareLock: () => {
        const state = get();
        if (state.lockReason === 'JWT_SINGLE') return;
        set({ contextLocked: false, lockReason: null });
      },

      clearWarehouse: () =>
        set({
          warehouseId: null,
          warehouse: null,
          contextLocked: false,
          lockReason: null,
        }),
    }),
    {
      name: 'invsys-active-warehouse',
      partialize: (state) => ({
        // Never persist lock — recompute from JWT / hardware on boot.
        warehouseId: state.contextLocked ? null : state.warehouseId,
        warehouse: state.contextLocked ? null : state.warehouse,
      }),
    }
  )
);

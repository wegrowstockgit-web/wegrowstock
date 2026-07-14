import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Warehouse } from '@/api/types';

interface ActiveWarehouseState {
  warehouseId: string | null;
  warehouse: Warehouse | null;
  setWarehouse: (warehouse: Warehouse) => void;
  clearWarehouse: () => void;
}

export const useActiveWarehouseStore = create<ActiveWarehouseState>()(
  persist(
    (set) => ({
      warehouseId: null,
      warehouse: null,

      setWarehouse: (warehouse) =>
        set({ warehouseId: warehouse.id, warehouse }),

      clearWarehouse: () => set({ warehouseId: null, warehouse: null }),
    }),
    {
      name: 'invsys-active-warehouse',
    }
  )
);

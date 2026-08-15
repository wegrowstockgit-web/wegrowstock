import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface SyncConflict {
  id: string;
  idempotencyKey: string;
  method: string;
  url: string;
  body?: unknown;
  status: number;
  message: string;
  failedAt: number;
}

interface SyncConflictState {
  syncConflicts: SyncConflict[];
  addConflict: (conflict: SyncConflict) => void;
  dismissConflict: (id: string) => void;
  clearConflicts: () => void;
}

export const useSyncConflictStore = create<SyncConflictState>()(
  persist(
    (set) => ({
      syncConflicts: [],

      addConflict: (conflict) =>
        set((state) => ({
          syncConflicts: [
            conflict,
            ...state.syncConflicts.filter((c) => c.id !== conflict.id),
          ].slice(0, 25),
        })),

      dismissConflict: (id) =>
        set((state) => ({
          syncConflicts: state.syncConflicts.filter((c) => c.id !== id),
        })),

      clearConflicts: () => set({ syncConflicts: [] }),
    }),
    {
      name: 'invsys-sync-conflicts',
      partialize: (state) => ({ syncConflicts: state.syncConflicts }),
    }
  )
);

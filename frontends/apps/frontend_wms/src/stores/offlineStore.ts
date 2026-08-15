import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** Offline mutation that failed with a business conflict (typically HTTP 409). */
export interface QuarantinedMutation {
  id: string;
  idempotencyKey: string;
  method: string;
  url: string;
  body?: unknown;
  status: number;
  /** RFC 7807 title / error code */
  title: string;
  /** Human-readable rejection reason (Problem Details detail) */
  detail: string;
  failedAt: number;
}

interface OfflineStoreState {
  quarantinedMutations: QuarantinedMutation[];
  quarantineMutation: (entry: QuarantinedMutation) => void;
  discardQuarantined: (id: string) => void;
  clearQuarantined: () => void;
}

export const useOfflineStore = create<OfflineStoreState>()(
  persist(
    (set) => ({
      quarantinedMutations: [],

      quarantineMutation: (entry) =>
        set((state) => ({
          quarantinedMutations: [
            entry,
            ...state.quarantinedMutations.filter((m) => m.id !== entry.id),
          ].slice(0, 50),
        })),

      discardQuarantined: (id) =>
        set((state) => ({
          quarantinedMutations: state.quarantinedMutations.filter((m) => m.id !== id),
        })),

      clearQuarantined: () => set({ quarantinedMutations: [] }),
    }),
    {
      name: 'invsys-offline-quarantine',
      partialize: (state) => ({ quarantinedMutations: state.quarantinedMutations }),
    },
  ),
);

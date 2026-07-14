import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface RailState {
  /** When true, the icon rail stays expanded and content padding follows. */
  pinned: boolean;
  setPinned: (pinned: boolean) => void;
  togglePinned: () => void;
}

export const useRailStore = create<RailState>()(
  persist(
    (set, get) => ({
      pinned: false,
      setPinned: (pinned) => set({ pinned }),
      togglePinned: () => set({ pinned: !get().pinned }),
    }),
    {
      name: 'invsys-icon-rail',
      partialize: (state) => ({ pinned: state.pinned }),
    }
  )
);

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface RailState {
  /** When true, the icon rail stays expanded and content padding follows (desktop). */
  pinned: boolean;
  setPinned: (pinned: boolean) => void;
  togglePinned: () => void;
  /** Tablet / phone overlay drawer open state (not persisted). */
  mobileOpen: boolean;
  setMobileOpen: (open: boolean) => void;
  toggleMobileOpen: () => void;
  /** Scroll-fold indicators for the icon deck (ephemeral). */
  canScrollUp: boolean;
  canScrollDown: boolean;
  setScrollFold: (canScrollUp: boolean, canScrollDown: boolean) => void;
}

export const useRailStore = create<RailState>()(
  persist(
    (set, get) => ({
      pinned: false,
      setPinned: (pinned) => set({ pinned }),
      togglePinned: () => set({ pinned: !get().pinned }),
      mobileOpen: false,
      setMobileOpen: (mobileOpen) => set({ mobileOpen }),
      toggleMobileOpen: () => set({ mobileOpen: !get().mobileOpen }),
      canScrollUp: false,
      canScrollDown: false,
      setScrollFold: (canScrollUp, canScrollDown) => set({ canScrollUp, canScrollDown }),
    }),
    {
      name: 'invsys-icon-rail',
      partialize: (state) => ({ pinned: state.pinned }),
    }
  )
);

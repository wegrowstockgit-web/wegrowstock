import { create } from 'zustand';

interface ScanBufferState {
  buffer: string;
  lastScan: string | null;
  lastScanAt: number | null;
  append: (char: string) => void;
  reset: () => void;
  commit: (value: string) => void;
  /** Clear wedge buffer + last-scan card after exception shunt. */
  clearScanCard: () => void;
}

export const useScanBufferStore = create<ScanBufferState>((set) => ({
  buffer: '',
  lastScan: null,
  lastScanAt: null,

  append: (char) =>
    set((state) => ({ buffer: state.buffer + char })),

  reset: () => set({ buffer: '' }),

  commit: (value) =>
    set({
      buffer: '',
      lastScan: value,
      lastScanAt: Date.now(),
    }),

  clearScanCard: () =>
    set({
      buffer: '',
      lastScan: null,
      lastScanAt: null,
    }),
}));

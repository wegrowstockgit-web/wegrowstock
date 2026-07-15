import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type DensityMode = 'compact' | 'cozy' | 'spacious';

interface PreferencesState {
  densityMode: DensityMode;
  setDensityMode: (mode: DensityMode) => void;
}

export const DENSITY_MODES: readonly DensityMode[] = ['compact', 'cozy', 'spacious'] as const;

export const DENSITY_LABELS: Record<DensityMode, string> = {
  compact: 'Compact',
  cozy: 'Cozy',
  spacious: 'Spacious',
};

/** Row / cell / type classes for each density tier (Surface A tables). */
export const DENSITY_STYLES: Record<
  DensityMode,
  { cell: string; typography: string; row: string; rowPx: number }
> = {
  compact: {
    cell: 'py-1 px-2',
    typography: 'text-xs',
    row: 'h-8',
    rowPx: 32,
  },
  cozy: {
    cell: 'py-2 px-4',
    typography: 'text-sm',
    row: 'h-11',
    rowPx: 44,
  },
  spacious: {
    cell: 'py-4 px-6',
    typography: 'text-base',
    row: 'h-16',
    rowPx: 64,
  },
};

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      densityMode: 'cozy',
      setDensityMode: (densityMode) => set({ densityMode }),
    }),
    {
      name: 'invsys-preferences',
      partialize: (state) => ({ densityMode: state.densityMode }),
    },
  ),
);

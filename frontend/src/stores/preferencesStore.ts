import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type DensityMode = 'compact' | 'cozy' | 'spacious';

export type TourId = 'office' | 'floor' | 'receiving-to-allocation';

interface PreferencesState {
  densityMode: DensityMode;
  setDensityMode: (mode: DensityMode) => void;
  /** When true, prompt for interactive driver.js tour after login. */
  showOnboardingTour: boolean;
  setShowOnboardingTour: (show: boolean) => void;

  /** Cross-route workflow tour machine (persisted). */
  activeTourId: TourId | null;
  currentTourStep: number;
  isTourAwaitingRoute: boolean;
  awaitingRoute: string | null;
  startTour: (tourId: TourId, stepIndex?: number) => void;
  setTourStep: (stepIndex: number) => void;
  setAwaitingRoute: (route: string | null) => void;
  clearTour: () => void;
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
      showOnboardingTour: true,
      setShowOnboardingTour: (showOnboardingTour) => set({ showOnboardingTour }),

      activeTourId: null,
      currentTourStep: 0,
      isTourAwaitingRoute: false,
      awaitingRoute: null,

      startTour: (tourId, stepIndex = 0) =>
        set({
          activeTourId: tourId,
          currentTourStep: stepIndex,
          isTourAwaitingRoute: false,
          awaitingRoute: null,
          showOnboardingTour: false,
        }),

      setTourStep: (currentTourStep) => set({ currentTourStep }),

      setAwaitingRoute: (route) =>
        set((state) => {
          const isTourAwaitingRoute = route != null;
          if (state.awaitingRoute === route && state.isTourAwaitingRoute === isTourAwaitingRoute) {
            return state;
          }
          return { isTourAwaitingRoute, awaitingRoute: route };
        }),

      clearTour: () =>
        set((state) => {
          if (
            state.activeTourId == null &&
            state.currentTourStep === 0 &&
            !state.isTourAwaitingRoute &&
            state.awaitingRoute == null
          ) {
            return state;
          }
          return {
            activeTourId: null,
            currentTourStep: 0,
            isTourAwaitingRoute: false,
            awaitingRoute: null,
          };
        }),
    }),
    {
      name: 'invsys-preferences',
      partialize: (state) => ({
        densityMode: state.densityMode,
        showOnboardingTour: state.showOnboardingTour,
        activeTourId: state.activeTourId,
        currentTourStep: state.currentTourStep,
        isTourAwaitingRoute: state.isTourAwaitingRoute,
        awaitingRoute: state.awaitingRoute,
      }),
    },
  ),
);

/** Test / E2E seam — start multi-page tours without a full document reload. */
export function installPreferencesTestHook(): void {
  if (typeof window === 'undefined') return;
  (
    window as Window & {
      __INVSYS_PREFERENCES__?: {
        startTour: (tourId: TourId, stepIndex?: number) => void;
        clearTour: () => void;
      };
    }
  ).__INVSYS_PREFERENCES__ = {
    startTour: (tourId, stepIndex) => usePreferencesStore.getState().startTour(tourId, stepIndex),
    clearTour: () => usePreferencesStore.getState().clearTour(),
  };
}

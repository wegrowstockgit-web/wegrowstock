import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { persistLanguage, type SupportedLanguage } from '@/lib/i18n';

export type DensityMode = 'compact' | 'cozy' | 'spacious';

export type TourId = 'office' | 'floor' | 'receiving-to-allocation';

/** Registered by TourOrchestrator so transitionToSubpage can tear down driver.js safely. */
let destroyActiveTourDriver: (() => void) | null = null;

export function registerTourDriverDestroy(fn: (() => void) | null): void {
  destroyActiveTourDriver = fn;
}

interface PreferencesState {
  densityMode: DensityMode;
  setDensityMode: (mode: DensityMode) => void;
  /** Per-grid Cozy/Compact/Spacious overrides. Missing keys fall back to densityMode. */
  tableDensityById: Record<string, DensityMode>;
  setTableDensity: (gridId: string, mode: DensityMode) => void;
  language: SupportedLanguage;
  setLanguage: (language: SupportedLanguage) => void;
  /** Tenant-admin office idle timeout (minutes). Not persisted locally. */
  desktopIdleTimeoutMinutes: number;
  setDesktopIdleTimeoutMinutes: (minutes: number) => void;
  /** When true, prompt for interactive driver.js tour after login. */
  showOnboardingTour: boolean;
  setShowOnboardingTour: (show: boolean) => void;

  /** Cross-route workflow tour machine (persisted). */
  activeTourId: TourId | null;
  currentTourStep: number;
  /** Gate: driver destroyed; waiting for targetRoute to mount before resume. */
  isTourMovingRoutes: boolean;
  targetRoute: string | null;
  startTour: (tourId: TourId, stepIndex?: number) => void;
  setTourStep: (stepIndex: number) => void;
  /**
   * Destroy the live driver, set the next step + target pathname, then let the
   * caller navigate (href may include query params).
   */
  transitionToSubpage: (route: string, nextStep: number) => void;
  /** Clear the inter-page gate after the destination has resumed the tour. */
  clearRouteTransition: () => void;
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

function pathnameOnly(route: string): string {
  const q = route.indexOf('?');
  return q >= 0 ? route.slice(0, q) : route;
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      densityMode: 'cozy',
      setDensityMode: (densityMode) => set({ densityMode }),
      tableDensityById: {},
      setTableDensity: (gridId, mode) =>
        set((state) => ({
          tableDensityById: { ...state.tableDensityById, [gridId]: mode },
        })),
      language: 'en',
      setLanguage: (language) => {
        persistLanguage(language);
        set({ language });
      },
      desktopIdleTimeoutMinutes: 30,
      setDesktopIdleTimeoutMinutes: (minutes) => {
        const allowed = minutes === 15 || minutes === 30 || minutes === 60 || minutes === 240;
        set({ desktopIdleTimeoutMinutes: allowed ? minutes : 30 });
      },
      showOnboardingTour: true,
      setShowOnboardingTour: (showOnboardingTour) => set({ showOnboardingTour }),

      activeTourId: null,
      currentTourStep: 0,
      isTourMovingRoutes: false,
      targetRoute: null,

      startTour: (tourId, stepIndex = 0) =>
        set({
          activeTourId: tourId,
          currentTourStep: stepIndex,
          isTourMovingRoutes: false,
          targetRoute: null,
          showOnboardingTour: false,
        }),

      setTourStep: (currentTourStep) => set({ currentTourStep }),

      transitionToSubpage: (route, nextStep) => {
        try {
          destroyActiveTourDriver?.();
        } catch {
          /* already torn down */
        }
        set({
          isTourMovingRoutes: true,
          targetRoute: pathnameOnly(route),
          currentTourStep: nextStep,
        });
      },

      clearRouteTransition: () =>
        set((state) => {
          if (!state.isTourMovingRoutes && state.targetRoute == null) return state;
          return { isTourMovingRoutes: false, targetRoute: null };
        }),

      clearTour: () =>
        set((state) => {
          if (
            state.activeTourId == null &&
            state.currentTourStep === 0 &&
            !state.isTourMovingRoutes &&
            state.targetRoute == null
          ) {
            return state;
          }
          return {
            activeTourId: null,
            currentTourStep: 0,
            isTourMovingRoutes: false,
            targetRoute: null,
          };
        }),
    }),
    {
      name: 'invsys-preferences',
      partialize: (state) => ({
        densityMode: state.densityMode,
        tableDensityById: state.tableDensityById,
        language: state.language,
        showOnboardingTour: state.showOnboardingTour,
        activeTourId: state.activeTourId,
        currentTourStep: state.currentTourStep,
        isTourMovingRoutes: state.isTourMovingRoutes,
        targetRoute: state.targetRoute,
      }),
      // Migrate pre-rename keys from older clients.
      merge: (persisted, current) => {
        const p = (persisted ?? {}) as Record<string, unknown>;
        const migrating = {
          ...current,
          ...p,
          isTourMovingRoutes:
            (p.isTourMovingRoutes as boolean | undefined) ??
            (p.isTourAwaitingRoute as boolean | undefined) ??
            current.isTourMovingRoutes,
          targetRoute:
            (p.targetRoute as string | null | undefined) ??
            (p.awaitingRoute as string | null | undefined) ??
            current.targetRoute,
        };
        delete (migrating as Record<string, unknown>).isTourAwaitingRoute;
        delete (migrating as Record<string, unknown>).awaitingRoute;
        return migrating as PreferencesState;
      },
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
        transitionToSubpage: (route: string, nextStep: number) => void;
      };
    }
  ).__INVSYS_PREFERENCES__ = {
    startTour: (tourId, stepIndex) => usePreferencesStore.getState().startTour(tourId, stepIndex),
    clearTour: () => usePreferencesStore.getState().clearTour(),
    transitionToSubpage: (route, nextStep) =>
      usePreferencesStore.getState().transitionToSubpage(route, nextStep),
  };
}

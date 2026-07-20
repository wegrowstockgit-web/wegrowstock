import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  registerTourDriverDestroy,
  usePreferencesStore,
} from '@/stores/preferencesStore';

describe('preferencesStore tour machine', () => {
  beforeEach(() => {
    localStorage.clear();
    registerTourDriverDestroy(null);
    usePreferencesStore.setState({
      densityMode: 'cozy',
      showOnboardingTour: true,
      activeTourId: null,
      currentTourStep: 0,
      isTourMovingRoutes: false,
      targetRoute: null,
    });
  });

  it('startTour sets active tour and clears onboarding prompt', () => {
    usePreferencesStore.getState().startTour('receiving-to-allocation', 0);
    const state = usePreferencesStore.getState();
    expect(state.activeTourId).toBe('receiving-to-allocation');
    expect(state.currentTourStep).toBe(0);
    expect(state.isTourMovingRoutes).toBe(false);
    expect(state.targetRoute).toBeNull();
    expect(state.showOnboardingTour).toBe(false);
  });

  it('transitionToSubpage destroys driver and gates the route hop', () => {
    const destroy = vi.fn();
    registerTourDriverDestroy(destroy);
    usePreferencesStore.getState().startTour('receiving-to-allocation');
    usePreferencesStore.getState().transitionToSubpage('/inbound/receive?po=PO-2026-00001', 2);

    expect(destroy).toHaveBeenCalledOnce();
    const state = usePreferencesStore.getState();
    expect(state.isTourMovingRoutes).toBe(true);
    expect(state.targetRoute).toBe('/inbound/receive');
    expect(state.currentTourStep).toBe(2);

    usePreferencesStore.getState().clearRouteTransition();
    expect(usePreferencesStore.getState().isTourMovingRoutes).toBe(false);
    expect(usePreferencesStore.getState().targetRoute).toBeNull();
  });

  it('setTourStep and clearTour reset the machine', () => {
    usePreferencesStore.getState().startTour('receiving-to-allocation', 1);
    usePreferencesStore.getState().setTourStep(2);
    expect(usePreferencesStore.getState().currentTourStep).toBe(2);

    usePreferencesStore.getState().clearTour();
    const state = usePreferencesStore.getState();
    expect(state.activeTourId).toBeNull();
    expect(state.currentTourStep).toBe(0);
    expect(state.isTourMovingRoutes).toBe(false);
    expect(state.targetRoute).toBeNull();
  });
});

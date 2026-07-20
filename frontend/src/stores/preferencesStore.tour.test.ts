import { beforeEach, describe, expect, it } from 'vitest';
import { usePreferencesStore } from '@/stores/preferencesStore';

describe('preferencesStore tour machine', () => {
  beforeEach(() => {
    localStorage.clear();
    usePreferencesStore.setState({
      densityMode: 'cozy',
      showOnboardingTour: true,
      activeTourId: null,
      currentTourStep: 0,
      isTourAwaitingRoute: false,
      awaitingRoute: null,
    });
  });

  it('startTour sets active tour and clears onboarding prompt', () => {
    usePreferencesStore.getState().startTour('receiving-to-allocation', 0);
    const state = usePreferencesStore.getState();
    expect(state.activeTourId).toBe('receiving-to-allocation');
    expect(state.currentTourStep).toBe(0);
    expect(state.isTourAwaitingRoute).toBe(false);
    expect(state.awaitingRoute).toBeNull();
    expect(state.showOnboardingTour).toBe(false);
  });

  it('setAwaitingRoute toggles cross-route pause', () => {
    usePreferencesStore.getState().startTour('receiving-to-allocation');
    usePreferencesStore.getState().setAwaitingRoute('/inbound/receive');
    expect(usePreferencesStore.getState().isTourAwaitingRoute).toBe(true);
    expect(usePreferencesStore.getState().awaitingRoute).toBe('/inbound/receive');

    usePreferencesStore.getState().setAwaitingRoute(null);
    expect(usePreferencesStore.getState().isTourAwaitingRoute).toBe(false);
    expect(usePreferencesStore.getState().awaitingRoute).toBeNull();
  });

  it('setTourStep and clearTour reset the machine', () => {
    usePreferencesStore.getState().startTour('receiving-to-allocation', 1);
    usePreferencesStore.getState().setTourStep(2);
    expect(usePreferencesStore.getState().currentTourStep).toBe(2);

    usePreferencesStore.getState().clearTour();
    const state = usePreferencesStore.getState();
    expect(state.activeTourId).toBeNull();
    expect(state.currentTourStep).toBe(0);
    expect(state.isTourAwaitingRoute).toBe(false);
    expect(state.awaitingRoute).toBeNull();
  });
});

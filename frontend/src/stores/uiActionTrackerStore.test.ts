import { beforeEach, describe, expect, it } from 'vitest';
import {
  resetUiActionTrackerForTests,
  useUiActionTrackerStore,
} from './uiActionTrackerStore';

describe('uiActionTrackerStore', () => {
  beforeEach(() => {
    resetUiActionTrackerForTests();
  });

  it('keeps a rolling buffer of the last 10 actions via trackAction', () => {
    const trackAction = useUiActionTrackerStore.getState().trackAction;
    for (let i = 0; i < 12; i++) {
      trackAction({ actionType: 'CLICK', elementLabel: `Button ${i}` });
    }
    const actions = useUiActionTrackerStore.getState().actions;
    expect(actions).toHaveLength(10);
    expect(actions[0].elementLabel).toBe('Button 2');
    expect(actions[9].elementLabel).toBe('Button 11');
  });

  it('getRecentBreadcrumbs returns last N with error messages', () => {
    useUiActionTrackerStore.getState().trackAction({
      actionType: 'TOAST_ERROR',
      elementLabel: 'Save Settings',
      errorMessage: 'Quantity must be positive',
    });
    useUiActionTrackerStore.getState().trackAction({
      actionType: 'CLICK',
      elementLabel: 'Confirm Receiving',
    });
    const recent = useUiActionTrackerStore.getState().getRecentBreadcrumbs(5);
    expect(recent).toHaveLength(2);
    expect(recent[0].errorMessage).toBe('Quantity must be positive');
    expect(recent[1].elementLabel).toBe('Confirm Receiving');
  });
});

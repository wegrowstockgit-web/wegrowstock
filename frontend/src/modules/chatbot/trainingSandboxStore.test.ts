import { beforeEach, describe, expect, it } from 'vitest';
import { useTrainingSandboxStore } from './trainingSandboxStore';

describe('trainingSandboxStore', () => {
  beforeEach(() => {
    useTrainingSandboxStore.getState().stopScenario();
  });

  it('starts picker inbound mission and advances on matching labels', () => {
    useTrainingSandboxStore.getState().startScenario('PICKER_INBOUND');
    expect(useTrainingSandboxStore.getState().isTrainingMode()).toBe(true);
    expect(useTrainingSandboxStore.getState().lastFeedback).toMatch(/no live stock/i);

    useTrainingSandboxStore.getState().onUiAction('Open Receiving');
    expect(useTrainingSandboxStore.getState().stepIndex).toBe(1);
    expect(useTrainingSandboxStore.getState().lastFeedback).toMatch(/SKU/i);

    useTrainingSandboxStore.getState().onUiAction('Confirm Receiving');
    expect(useTrainingSandboxStore.getState().stepIndex).toBe(2);

    useTrainingSandboxStore.getState().onUiAction('Complete putaway');
    expect(useTrainingSandboxStore.getState().completed).toBe(true);
  });

  it('manager allocation scenario completes on allocate click', () => {
    useTrainingSandboxStore.getState().startScenario('MANAGER_ALLOCATION');
    useTrainingSandboxStore.getState().onUiAction('Sales Orders');
    useTrainingSandboxStore.getState().onUiAction('Clear credit hold');
    useTrainingSandboxStore.getState().onUiAction('Allocate');
    expect(useTrainingSandboxStore.getState().completed).toBe(true);
    expect(useTrainingSandboxStore.getState().lastFeedback).toMatch(/reserved/i);
  });

  it('ignores unrelated clicks and stop clears training mode', () => {
    useTrainingSandboxStore.getState().startScenario('PICKER_INBOUND');
    useTrainingSandboxStore.getState().onUiAction('Unrelated Dashboard');
    expect(useTrainingSandboxStore.getState().stepIndex).toBe(0);
    useTrainingSandboxStore.getState().stopScenario();
    expect(useTrainingSandboxStore.getState().isTrainingMode()).toBe(false);
  });

  it('records blocked mutations locally without leaving training mode', () => {
    useTrainingSandboxStore.getState().startScenario('MANAGER_ALLOCATION');
    useTrainingSandboxStore.getState().recordBlockedMutation('post', '/api/v1/inventory/adjust');
    expect(useTrainingSandboxStore.getState().blockedMutations).toHaveLength(1);
    expect(useTrainingSandboxStore.getState().lastFeedback).toMatch(/blocked in training/i);
    expect(useTrainingSandboxStore.getState().isTrainingMode()).toBe(true);
  });

  it('exposes flight-simulator aliases (isActive / toggleSandbox / exitSandbox)', () => {
    useTrainingSandboxStore.getState().toggleSandbox('PICKER_INBOUND');
    expect(useTrainingSandboxStore.getState().isActive).toBe(true);
    expect(useTrainingSandboxStore.getState().activeRole).toBe('PICKER_INBOUND');
    useTrainingSandboxStore.getState().exitSandbox();
    expect(useTrainingSandboxStore.getState().isActive).toBe(false);
    expect(useTrainingSandboxStore.getState().activeRole).toBeNull();
  });
});

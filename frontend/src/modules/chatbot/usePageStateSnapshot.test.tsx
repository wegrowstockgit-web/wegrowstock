import { beforeEach, describe, expect, it } from 'vitest';
import { renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { usePageStateSnapshot } from './usePageStateSnapshot';
import { useSessionStore } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useNetworkSyncStore } from '@/stores/networkSyncStore';
import {
  resetUiActionTrackerForTests,
  useUiActionTrackerStore,
} from '@/stores/uiActionTrackerStore';
import {
  clearSupportNetworkError,
  recordSupportNetworkError,
} from './supportNetworkTelemetry';

function wrapper(initial: string) {
  return function W({ children }: { children: ReactNode }) {
    return <MemoryRouter initialEntries={[initial]}>{children}</MemoryRouter>;
  };
}

describe('usePageStateSnapshot', () => {
  beforeEach(() => {
    clearSupportNetworkError();
    resetUiActionTrackerForTests();
  });

  it('captures route, roles, warehouse, filters, and network phase', () => {
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'mgr@demo.test',
        displayName: 'Mgr',
        roles: ['WAREHOUSE_MANAGER'],
        warehouseIds: ['wh-1'],
        avatarUrl: null,
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    });
    useActiveWarehouseStore.setState({
      warehouseId: 'wh-1',
      warehouse: {
        id: 'wh-1',
        name: 'Main DC',
        code: 'MAIN',
      },
      contextLocked: false,
      lockReason: null,
    });
    useNetworkSyncStore.setState({ online: true, syncing: false, pendingCount: 0 });

    const { result } = renderHook(() => usePageStateSnapshot(), {
      wrapper: wrapper('/sales-orders?status=BACKORDERED&id=SO-9'),
    });

    expect(result.current.routePath).toBe('/sales-orders?status=BACKORDERED&id=SO-9');
    expect(result.current.userRoles).toContain('WAREHOUSE_MANAGER');
    expect(result.current.activeWarehouseId).toBe('wh-1');
    expect(result.current.activeFilter).toContain('status=BACKORDERED');
    expect(result.current.selectedEntityId).toBe('SO-9');
    expect(result.current.selectedEntity).toBe('SO-9');
    expect(result.current.networkPhase).toBe('online');
    expect(result.current.networkState).toBe('online');
    expect(result.current.trace_id).toBeNull();
    expect(result.current.lastHttpErrorStatus).toBeNull();
  });

  it('includes fresh HTTP error status and trace_id for copilot grounding', () => {
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'mgr@demo.test',
        displayName: 'Mgr',
        roles: ['WAREHOUSE_MANAGER'],
        warehouseIds: ['wh-1'],
        avatarUrl: null,
        tenantId: 't1',
      },
      lastRequestId: 'fallback-req',
      primarySession: null,
    });
    useActiveWarehouseStore.setState({
      warehouseId: 'wh-1',
      warehouse: { id: 'wh-1', name: 'Main DC', code: 'MAIN' },
      contextLocked: false,
      lockReason: null,
    });
    useNetworkSyncStore.setState({ online: true, syncing: false, pendingCount: 0 });
    recordSupportNetworkError({
      status: 409,
      message: 'BIN_LOCKED_BY_CYCLE_COUNT',
      traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
    });

    const { result } = renderHook(() => usePageStateSnapshot(), {
      wrapper: wrapper('/inventory'),
    });

    expect(result.current.lastHttpErrorStatus).toBe(409);
    expect(result.current.lastHttpErrorMessage).toMatch(/BIN_LOCKED/i);
    expect(result.current.trace_id).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
  });

  it('appends the last 5 UI action breadcrumbs for temporal memory', () => {
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'mgr@demo.test',
        displayName: 'Mgr',
        roles: ['WAREHOUSE_MANAGER'],
        warehouseIds: ['wh-1'],
        avatarUrl: null,
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    });
    useActiveWarehouseStore.setState({
      warehouseId: 'wh-1',
      warehouse: { id: 'wh-1', name: 'Main DC', code: 'MAIN' },
      contextLocked: false,
      lockReason: null,
    });
    useNetworkSyncStore.setState({ online: true, syncing: false, pendingCount: 0 });

    for (let i = 0; i < 7; i++) {
      useUiActionTrackerStore.getState().trackAction({
        actionType: 'CLICK',
        elementLabel: `Action ${i}`,
      });
    }
    useUiActionTrackerStore.getState().trackAction({
      actionType: 'TOAST_ERROR',
      elementLabel: 'Save Settings',
      errorMessage: 'Validation failed',
    });

    const { result } = renderHook(() => usePageStateSnapshot(), {
      wrapper: wrapper('/inbound/receive'),
    });

    expect(result.current.recentBreadcrumbs).toHaveLength(5);
    expect(result.current.recentBreadcrumbs[0].elementLabel).toBe('Action 3');
    expect(result.current.recentBreadcrumbs.at(-1)?.actionType).toBe('TOAST_ERROR');
    expect(result.current.recentBreadcrumbs.at(-1)?.errorMessage).toBe('Validation failed');
  });
});

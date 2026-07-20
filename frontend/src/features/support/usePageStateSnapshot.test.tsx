import { describe, expect, it } from 'vitest';
import { renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { usePageStateSnapshot } from './usePageStateSnapshot';
import { useSessionStore } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useNetworkSyncStore } from '@/stores/networkSyncStore';

function wrapper(initial: string) {
  return function W({ children }: { children: ReactNode }) {
    return <MemoryRouter initialEntries={[initial]}>{children}</MemoryRouter>;
  };
}

describe('usePageStateSnapshot', () => {
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
    expect(result.current.selectedEntity).toBe('SO-9');
    expect(result.current.networkState).toBe('online');
  });
});

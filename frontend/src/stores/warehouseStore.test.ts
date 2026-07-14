import { describe, expect, it, beforeEach, vi } from 'vitest';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

vi.mock('@/stores/session', () => ({
  useSessionStore: {
    getState: () => ({
      user: {
        roles: ['PICKER'],
        warehouseIds: ['wh-1'],
      },
    }),
  },
}));

import { apiClient } from '@/api/client';
import { useWarehouseStore } from '@/stores/warehouseStore';

describe('warehouseStore', () => {
  beforeEach(() => {
    useWarehouseStore.getState().clear();
    vi.mocked(apiClient.get).mockReset();
  });

  it('loads allowed warehouses and disables switcher for picker', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{ id: 'wh-1', name: 'Main', code: 'WH-01' }],
    } as never);

    const allowed = await useWarehouseStore.getState().fetchAllowed();
    expect(allowed).toHaveLength(1);
    expect(useWarehouseStore.getState().switcherDisabled).toBe(true);
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/locations/warehouses/assigned');
  });
});

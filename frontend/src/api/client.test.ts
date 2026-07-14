import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { InternalAxiosRequestConfig } from 'axios';

const sessionState = {
  accessToken: 'tok-abc' as string | null,
  setLastRequestId: vi.fn(),
};

const warehouseState = {
  warehouseId: 'wh-uuid-1' as string | null,
};

vi.mock('@/stores/session', () => ({
  useSessionStore: {
    getState: () => sessionState,
  },
}));

vi.mock('@/stores/activeWarehouse', () => ({
  useActiveWarehouseStore: {
    getState: () => warehouseState,
  },
}));

vi.mock('@/offline/queryPersistence', () => ({
  clearQueryCache: vi.fn(),
  queryClient: { clear: vi.fn() },
}));

import { apiClient } from '@/api/client';

describe('apiClient warehouse header', () => {
  beforeEach(() => {
    sessionState.accessToken = 'tok-abc';
    warehouseState.warehouseId = 'wh-uuid-1';
    sessionState.setLastRequestId.mockReset();
  });

  it('attaches X-Warehouse-Id from active warehouse store', async () => {
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (c: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;
    const requestInterceptor = handlers.find((h) => h.fulfilled)?.fulfilled;
    expect(requestInterceptor).toBeTypeOf('function');

    const config = await requestInterceptor!({
      headers: {} as InternalAxiosRequestConfig['headers'],
      url: '/api/v1/locations/warehouses/assigned',
    } as InternalAxiosRequestConfig);

    expect(config.headers.Authorization).toBe('Bearer tok-abc');
    expect(config.headers['X-Warehouse-Id']).toBe('wh-uuid-1');
  });

  it('omits X-Warehouse-Id when no warehouse selected', async () => {
    warehouseState.warehouseId = null;
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (c: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;
    const requestInterceptor = handlers.find((h) => h.fulfilled)?.fulfilled;

    const config = await requestInterceptor!({
      headers: {} as InternalAxiosRequestConfig['headers'],
      url: '/api/v1/locations',
    } as InternalAxiosRequestConfig);

    expect(config.headers['X-Warehouse-Id']).toBeUndefined();
  });
});

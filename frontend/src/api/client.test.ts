import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { InternalAxiosRequestConfig } from 'axios';

const sessionState = {
  authenticated: true,
  setLastRequestId: vi.fn(),
  clearSession: vi.fn(),
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

const trainingGuard = {
  isTrainingMode: () => false,
  recordBlockedMutation: vi.fn(),
  onUiAction: vi.fn(),
};

vi.mock('@/lib/chatbot/active', () => ({
  recordSupportNetworkError: vi.fn(),
  getTrainingGuard: () => trainingGuard,
  ChatbotHost: () => null,
}));

import { apiClient } from '@/api/client';

describe('apiClient warehouse header', () => {
  beforeEach(() => {
    sessionState.authenticated = true;
    warehouseState.warehouseId = 'wh-uuid-1';
    sessionState.setLastRequestId.mockReset();
    trainingGuard.isTrainingMode = () => false;
    trainingGuard.recordBlockedMutation.mockReset();
  });

  it('attaches X-Warehouse-Id and uses credentials (no Bearer token)', async () => {
    expect(apiClient.defaults.withCredentials).toBe(true);
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (c: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;
    const requestInterceptor = handlers.find((h) => h.fulfilled)?.fulfilled;
    expect(requestInterceptor).toBeTypeOf('function');

    const config = await requestInterceptor!({
      headers: {} as InternalAxiosRequestConfig['headers'],
      url: '/api/v1/locations/warehouses/assigned',
    } as InternalAxiosRequestConfig);

    expect(config.headers.Authorization).toBeUndefined();
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

  it('clears Content-Type for FormData so multipart boundary is set', async () => {
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (c: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;
    const requestInterceptor = handlers.find((h) => h.fulfilled)?.fulfilled;
    const headers = {
      'Content-Type': 'application/json',
      set: vi.fn(),
    } as unknown as InternalAxiosRequestConfig['headers'];

    await requestInterceptor!({
      headers,
      url: '/api/v1/ingestion/preflight',
      data: new FormData(),
    } as InternalAxiosRequestConfig);

    expect(headers.set).toHaveBeenCalledWith('Content-Type', false);
  });

  it('blocks mutating inventory calls while training mode is active', async () => {
    trainingGuard.isTrainingMode = () => true;
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (c: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;
    const requestInterceptor = handlers.find((h) => h.fulfilled)?.fulfilled;

    await expect(
      requestInterceptor!({
        headers: {} as InternalAxiosRequestConfig['headers'],
        method: 'post',
        url: '/api/v1/inventory/adjust',
      } as InternalAxiosRequestConfig),
    ).rejects.toThrow(/Training mode is active/i);
    expect(trainingGuard.recordBlockedMutation).toHaveBeenCalledWith(
      'post',
      '/api/v1/inventory/adjust',
    );
  });

  it('allows support chat mutations during training mode', async () => {
    trainingGuard.isTrainingMode = () => true;
    const handlers = (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled?: (c: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
    }).handlers;
    const requestInterceptor = handlers.find((h) => h.fulfilled)?.fulfilled;

    const config = await requestInterceptor!({
      headers: {} as InternalAxiosRequestConfig['headers'],
      method: 'post',
      url: '/api/v1/support/chat',
    } as InternalAxiosRequestConfig);

    expect(config.url).toContain('/support/chat');
  });
});

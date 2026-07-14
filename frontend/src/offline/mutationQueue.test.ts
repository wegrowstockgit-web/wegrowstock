import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockRequest, mockEnsureFresh, mockAddConflict } = vi.hoisted(() => ({
  mockRequest: vi.fn(),
  mockEnsureFresh: vi.fn(),
  mockAddConflict: vi.fn(),
}));

vi.mock('idb-keyval', () => {
  let store: unknown;
  return {
    get: vi.fn(async () => store),
    set: vi.fn(async (_key: string, value: unknown) => {
      store = value;
    }),
    __reset: () => {
      store = undefined;
    },
  };
});

vi.mock('@/api/client', () => ({
  apiClient: { request: mockRequest },
  ensureFreshSession: mockEnsureFresh,
}));

vi.mock('@/stores/syncConflicts', () => ({
  useSyncConflictStore: {
    getState: () => ({
      addConflict: mockAddConflict,
    }),
  },
}));

import * as idb from 'idb-keyval';
import {
  enqueueMutation,
  getMutationQueue,
  replayMutationQueue,
} from './mutationQueue';

describe('mutationQueue secure offline engine', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    // @ts-expect-error test helper
    idb.__reset?.();
    await idb.set('invsys-mutation-queue', []);
    mockEnsureFresh.mockResolvedValue(true);
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
  });

  it('refreshes session then replays with Idempotency-Key', async () => {
    mockRequest.mockResolvedValue({ data: {} });
    await enqueueMutation({
      idempotencyKey: 'idem-1',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'ABC' },
    });

    const result = await replayMutationQueue();

    expect(mockEnsureFresh).toHaveBeenCalled();
    expect(mockRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'POST',
        url: '/api/v1/fulfillment/scan',
        headers: { 'Idempotency-Key': 'idem-1' },
      })
    );
    expect(result.succeeded).toBe(1);
    expect(await getMutationQueue()).toHaveLength(0);
  });

  it('dead-letters 4xx business errors into syncConflicts', async () => {
    const axiosError = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: { status: 409, data: { message: 'Stock no longer available' } },
    });
    // Make axios.isAxiosError return true
    const axios = await import('axios');
    vi.spyOn(axios.default, 'isAxiosError').mockReturnValue(true);

    mockRequest.mockRejectedValue(axiosError);
    await enqueueMutation({
      idempotencyKey: 'idem-2',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'XYZ' },
    });

    const result = await replayMutationQueue();

    expect(result.deadLettered).toBe(1);
    expect(mockAddConflict).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 409,
        message: 'Stock no longer available',
        url: '/api/v1/fulfillment/scan',
      })
    );
    expect(await getMutationQueue()).toHaveLength(0);
  });

  it('skips flush when session refresh fails', async () => {
    mockEnsureFresh.mockResolvedValue(false);
    await enqueueMutation({
      idempotencyKey: 'idem-3',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: {},
    });

    const result = await replayMutationQueue();
    expect(result.succeeded).toBe(0);
    expect(mockRequest).not.toHaveBeenCalled();
    expect(await getMutationQueue()).toHaveLength(1);
  });
});

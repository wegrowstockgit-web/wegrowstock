import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useCryptoMemoryKeyStore } from '@/stores/cryptoMemoryKeyStore';

const { mockRequest, mockEnsureFresh, mockAddConflict, mockQuarantine } = vi.hoisted(() => ({
  mockRequest: vi.fn(),
  mockEnsureFresh: vi.fn(),
  mockAddConflict: vi.fn(),
  mockQuarantine: vi.fn(),
}));

vi.mock('idb-keyval', () => {
  const store = new Map<string, unknown>();
  return {
    get: vi.fn(async (key: string) => store.get(key)),
    set: vi.fn(async (key: string, value: unknown) => {
      store.set(key, value);
    }),
    del: vi.fn(async (key: string) => {
      store.delete(key);
    }),
    __reset: () => {
      store.clear();
    },
  };
});

vi.mock('@/api/client', () => ({
  apiClient: { request: mockRequest },
  ensureFreshSession: mockEnsureFresh,
}));

vi.mock('@/stores/offlineStore', () => ({
  useOfflineStore: {
    getState: () => ({
      quarantineMutation: mockQuarantine,
    }),
  },
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
    useCryptoMemoryKeyStore.getState().clearKey();
    await useCryptoMemoryKeyStore.getState().ensureKey();
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
        headers: { 'Idempotency-Key': 'idem-1', 'X-Offline-Replay': 'true' },
      }),
    );
    expect(result.succeeded).toBe(1);
    expect(await getMutationQueue()).toHaveLength(0);
  });

  it('quarantines 409 conflicts without stalling the queue', async () => {
    const axiosError = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: {
        status: 409,
        data: {
          title: 'ALLOCATION_LOCKED',
          detail: 'Task reassigned to another picker',
        },
      },
    });
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
    expect(mockQuarantine).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 409,
        title: 'ALLOCATION_LOCKED',
        detail: 'Task reassigned to another picker',
        url: '/api/v1/fulfillment/scan',
      }),
    );
    expect(mockAddConflict).toHaveBeenCalled();
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

  it('replays pre-parsed GS1 scan body without requiring server-side AI decode', async () => {
    mockRequest.mockResolvedValue({ data: { sku: 'GS1R-1' } });
    const gs1Body = {
      barcode: '01234567890128',
      mode: 'receive',
      gtin: '01234567890128',
      lotNumber: 'BATCH-E2E',
      expiryDate: '2025-12-31',
      quantity: 4,
      isGs1: true,
      rawBarcode: '(01)01234567890128(10)BATCH-E2E(17)251231(30)4',
      metadata: { vendor_lot_captured: 'BATCH-E2E' },
    };
    await enqueueMutation({
      idempotencyKey: 'idem-gs1',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: gs1Body,
    });

    await replayMutationQueue();

    expect(mockRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          barcode: '01234567890128',
          lotNumber: 'BATCH-E2E',
          quantity: 4,
          isGs1: true,
          metadata: { vendor_lot_captured: 'BATCH-E2E' },
        }),
      }),
    );
  });

  it('keeps transient 5xx failures in the queue for retry', async () => {
    const axiosError = Object.assign(new Error('Server'), {
      isAxiosError: true,
      response: { status: 503, data: { detail: 'unavailable' } },
    });
    const axios = await import('axios');
    vi.spyOn(axios.default, 'isAxiosError').mockReturnValue(true);
    mockRequest.mockRejectedValue(axiosError);

    await enqueueMutation({
      idempotencyKey: 'idem-5xx',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'X' },
    });

    const result = await replayMutationQueue();
    expect(result.failed).toBe(1);
    expect(result.deadLettered).toBe(0);
    expect(await getMutationQueue()).toHaveLength(1);
  });

  it('drains the queue chronologically by scannedAt', async () => {
    const order: string[] = [];
    mockRequest.mockImplementation(async (config: { headers?: { 'Idempotency-Key'?: string } }) => {
      order.push(config.headers?.['Idempotency-Key'] ?? '');
      return { data: {} };
    });

    await enqueueMutation({
      idempotencyKey: 'later',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'B' },
      scannedAt: 2_000,
    });
    await enqueueMutation({
      idempotencyKey: 'earlier',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'A' },
      scannedAt: 1_000,
    });

    await replayMutationQueue();
    expect(order).toEqual(['earlier', 'later']);
  });

  it('holds remaining queue on 401 after refresh failure', async () => {
    const axiosError = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401, data: { detail: 'expired' } },
    });
    const axios = await import('axios');
    vi.spyOn(axios.default, 'isAxiosError').mockReturnValue(true);

    mockRequest.mockRejectedValue(axiosError);
    // First ensureFreshSession (pre-flush) succeeds; mid-queue refresh fails.
    mockEnsureFresh.mockResolvedValueOnce(true).mockResolvedValueOnce(false);

    await enqueueMutation({
      idempotencyKey: 'idem-a',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'A' },
      scannedAt: 1,
    });
    await enqueueMutation({
      idempotencyKey: 'idem-b',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'B' },
      scannedAt: 2,
    });

    const result = await replayMutationQueue();
    expect(result.heldForAuth).toBe(2);
    expect(result.succeeded).toBe(0);
    expect(await getMutationQueue()).toHaveLength(2);
  });

  it('resumes after silent 401 refresh without dropping the queue', async () => {
    const axiosError = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401, data: { detail: 'expired' } },
    });
    const axios = await import('axios');
    vi.spyOn(axios.default, 'isAxiosError').mockReturnValue(true);

    mockRequest
      .mockRejectedValueOnce(axiosError)
      .mockResolvedValueOnce({ data: {} })
      .mockResolvedValueOnce({ data: {} });
    mockEnsureFresh.mockResolvedValue(true);

    await enqueueMutation({
      idempotencyKey: 'idem-r1',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'R1' },
      scannedAt: 1,
    });
    await enqueueMutation({
      idempotencyKey: 'idem-r2',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'R2' },
      scannedAt: 2,
    });

    const result = await replayMutationQueue();
    expect(result.succeeded).toBe(2);
    expect(result.heldForAuth).toBe(0);
    expect(await getMutationQueue()).toHaveLength(0);
    expect(mockEnsureFresh.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('startMutationQueueReplay flushes when online', async () => {
    mockRequest.mockResolvedValue({ data: {} });
    await enqueueMutation({
      idempotencyKey: 'idem-start',
      method: 'POST',
      url: '/api/v1/fulfillment/scan',
      body: { barcode: 'Y' },
    });

    const { startMutationQueueReplay } = await import('./mutationQueue');
    startMutationQueueReplay();
    await vi.waitFor(async () => {
      expect(await getMutationQueue()).toHaveLength(0);
    });
  });
});



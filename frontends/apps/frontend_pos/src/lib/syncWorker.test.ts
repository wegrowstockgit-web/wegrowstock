import { afterEach, describe, expect, it, vi } from 'vitest';
import { db } from './db';
import { flushOutbox, startOutboxPolling, toSyncPayload } from './syncWorker';

describe('syncWorker', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('skips when offline and no-ops on empty outbox', async () => {
    expect(await flushOutbox(vi.fn(), false)).toEqual({ flushed: 0, skipped: true });
    expect(await flushOutbox(vi.fn(), true)).toEqual({ flushed: 0, skipped: false });
  });

  it('posts the batch and deletes accepted rows', async () => {
    await db.outbox_receipts.put({
      id: 'r-ok',
      storeLocationId: '',
      taxRegion: 'US',
      tenderType: 'CASH',
      tenderAmount: 5,
      lines: [{ variantId: 'v1', upc: '1', quantity: 1, unitPrice: 5 }],
      createdAt: 1,
    });
    await db.outbox_receipts.put({
      id: 'r-bad',
      storeLocationId: 'store',
      taxRegion: 'MX',
      tenderType: 'CARD',
      tenderAmount: 5,
      lines: [{ variantId: 'v2', upc: '2', quantity: 1, unitPrice: 5 }],
      createdAt: 2,
    });

    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ accepted: 1, duplicates: 0, rejected: [{ receiptId: 'r-bad' }] }),
    });

    const result = await flushOutbox(fetchImpl, true);
    expect(result.flushed).toBe(1);
    expect(await db.outbox_receipts.toArray()).toHaveLength(1);
    expect(await db.outbox_receipts.get('r-bad')).toBeTruthy();
    expect(toSyncPayload([{
      id: 'x',
      storeLocationId: '',
      taxRegion: 'US',
      tenderType: 'CASH',
      tenderAmount: 1,
      lines: [{ variantId: 'v', upc: 'u', quantity: 1, unitPrice: 1 }],
      createdAt: 0,
    }])[0].storeLocationId).toBe('a0000000-0000-4000-8000-000000000601');
  });

  it('keeps the outbox when the API fails', async () => {
    await db.outbox_receipts.put({
      id: 'r1',
      storeLocationId: 's',
      taxRegion: 'US',
      tenderType: 'CASH',
      tenderAmount: 1,
      lines: [{ variantId: 'v', upc: 'u', quantity: 1, unitPrice: 1 }],
      createdAt: 1,
    });
    const result = await flushOutbox(vi.fn().mockResolvedValue({ ok: false, status: 500 }), true);
    expect(result.error).toBe('HTTP 500');
    expect(await db.outbox_receipts.count()).toBe(1);
  });

  it('polls and listens for online', async () => {
    const flush = vi.fn().mockResolvedValue({ flushed: 0, skipped: false });
    const stop = startOutboxPolling(10_000, flush);
    expect(flush).toHaveBeenCalled();
    window.dispatchEvent(new Event('online'));
    expect(flush).toHaveBeenCalledTimes(2);
    stop();
    window.dispatchEvent(new Event('online'));
    expect(flush).toHaveBeenCalledTimes(2);
  });
});

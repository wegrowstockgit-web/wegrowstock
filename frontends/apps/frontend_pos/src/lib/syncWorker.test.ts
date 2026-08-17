import { afterEach, describe, expect, it, vi } from 'vitest';
import { db, logPosEvent } from './db';
import { flushOutbox, startOutboxPolling, toAuditSyncPayload, toSyncPayload } from './syncWorker';

describe('syncWorker', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('skips when offline and no-ops on empty outbox', async () => {
    expect(await flushOutbox(vi.fn(), false)).toEqual({ flushed: 0, skipped: true, auditFlushed: 0 });
    expect(await flushOutbox(vi.fn(), true)).toEqual({ flushed: 0, skipped: false, auditFlushed: 0 });
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

  it('records transport failures without dropping the local trail', async () => {
    await db.outbox_receipts.put({
      id: 'r-throw',
      storeLocationId: 's',
      taxRegion: 'US',
      tenderType: 'CASH',
      tenderAmount: 1,
      lines: [{ variantId: 'v', upc: 'u', quantity: 1, unitPrice: 1 }],
      createdAt: 1,
    });
    await logPosEvent({
      timestamp: 12,
      cashierId: 'c1',
      eventType: 'PRICE_OVERRIDE',
      orderId: 'o3',
      valueVoided: 1,
    });
    const result = await flushOutbox(vi.fn().mockRejectedValue(new Error('offline-drop')), true);
    expect(result.error).toBe('offline-drop');
    expect(result.auditError).toBe('offline-drop');
    expect(await db.outbox_receipts.count()).toBe(1);
    expect(await db.audit_events.count()).toBe(1);
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

  it('pushes pending audit events without blocking a receipt failure', async () => {
    await db.outbox_receipts.put({
      id: 'r-fail',
      storeLocationId: 's',
      taxRegion: 'US',
      tenderType: 'CASH',
      tenderAmount: 1,
      lines: [{ variantId: 'v', upc: 'u', quantity: 1, unitPrice: 1 }],
      createdAt: 1,
    });
    const event = await logPosEvent({
      timestamp: 10,
      cashierId: 'c1',
      eventType: 'LINE_VOID',
      orderId: 'o1',
      productId: 'p1',
      valueVoided: 12.5,
    });

    const fetchImpl = vi.fn(async (url: string) => {
      if (String(url).includes('audit-sync')) {
        return {
          ok: true,
          json: async () => ({ accepted: 1, duplicates: 0, rejected: [] }),
        };
      }
      return { ok: false, status: 500 };
    });

    const result = await flushOutbox(fetchImpl as unknown as typeof fetch, true);
    expect(result.error).toBe('HTTP 500');
    expect(result.auditFlushed).toBe(1);
    expect(await db.outbox_receipts.count()).toBe(1);
    expect(await db.audit_events.count()).toBe(0);
    expect(toAuditSyncPayload([event])[0]).toMatchObject({
      id: event.id,
      eventType: 'LINE_VOID',
      valueVoided: 12.5,
    });
  });

  it('keeps rejected audit events in the local trail', async () => {
    const event = await logPosEvent({
      timestamp: 11,
      cashierId: 'c1',
      eventType: 'NO_SALE',
      orderId: 'o2',
      valueVoided: 0,
    });
    const result = await flushOutbox(vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ accepted: 0, rejected: [{ eventId: event.id }] }),
    }), true);
    expect(result.auditFlushed).toBe(0);
    expect(await db.audit_events.count()).toBe(1);
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

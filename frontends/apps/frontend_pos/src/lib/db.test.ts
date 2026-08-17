import { describe, expect, it } from 'vitest';
import {
  clearCartDraft,
  db,
  deleteOutboxReceipts,
  enqueueReceipt,
  loadActiveOrderId,
  loadCartDraft,
  logPosEvent,
  lookupCatalog,
  saveCartDraft,
} from './db';
import { DEMO_CATALOG, seedDemoCatalogIfEmpty } from './catalogSeed';

describe('pos db', () => {
  it('stores catalog, drafts, and outbox receipts', async () => {
    expect(await seedDemoCatalogIfEmpty()).toBe(DEMO_CATALOG.length);
    expect(await seedDemoCatalogIfEmpty()).toBe(0);
    expect(await lookupCatalog('7501234567890')).toMatchObject({ name: 'Agua 600ml' });
    expect(await lookupCatalog('   ')).toBeUndefined();

    await saveCartDraft([{ variantId: '1', upc: '1', name: 'A', unitPrice: 1, qty: 2 }], 'order-1');
    expect(await loadCartDraft()).toHaveLength(1);
    expect(await loadActiveOrderId()).toBe('order-1');
    await clearCartDraft();
    expect(await loadCartDraft()).toEqual([]);
    expect(await loadActiveOrderId()).toBeUndefined();

    const event = await logPosEvent({
      timestamp: 99,
      cashierId: 'cashier-1',
      eventType: 'TX_VOID',
      orderId: 'order-1',
      valueVoided: 40.25,
      managerOverrideId: 'mgr-1',
    });
    expect(event.id.charAt(14)).toBe('7');
    expect(await db.audit_events.get(event.id)).toMatchObject({
      eventType: 'TX_VOID',
      valueVoided: 40.25,
      managerOverrideId: 'mgr-1',
    });

    await enqueueReceipt({
      id: 'r1',
      storeLocationId: 's1',
      taxRegion: 'US',
      tenderType: 'CASH',
      tenderAmount: 10,
      lines: [{ variantId: '1', upc: '1', quantity: 1, unitPrice: 1 }],
      createdAt: 1,
    });
    expect(await db.outbox_receipts.count()).toBe(1);
    await deleteOutboxReceipts([]);
    expect(await db.outbox_receipts.count()).toBe(1);
    await deleteOutboxReceipts(['r1']);
    expect(await db.outbox_receipts.count()).toBe(0);
  });
});

import { describe, expect, it } from 'vitest';
import {
  clearCartDraft,
  db,
  deleteOutboxReceipts,
  enqueueReceipt,
  loadCartDraft,
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

    await saveCartDraft([{ variantId: '1', upc: '1', name: 'A', unitPrice: 1, qty: 2 }]);
    expect(await loadCartDraft()).toHaveLength(1);
    await clearCartDraft();
    expect(await loadCartDraft()).toEqual([]);

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

import Dexie, { type Table } from 'dexie';
import { uuidv7 } from './uuidv7';

export type TaxRegion = 'US' | 'MX';

export type PosAuditEventType = 'LINE_VOID' | 'TX_VOID' | 'NO_SALE' | 'PRICE_OVERRIDE';

export type CatalogCacheRow = {
  id: string;
  upc: string;
  name: string;
  unitPrice: number;
  imageUrl?: string;
};

export type CartLine = {
  variantId: string;
  upc: string;
  name: string;
  unitPrice: number;
  qty: number;
  imageUrl?: string;
};

export type CartDraftRow = {
  id: string;
  lines: CartLine[];
  orderId?: string;
  updatedAt: number;
};

export type OutboxReceiptLine = {
  variantId: string;
  upc: string;
  quantity: number;
  unitPrice: number;
};

export type OutboxReceiptRow = {
  id: string;
  storeLocationId: string;
  taxRegion: TaxRegion;
  tenderType: string;
  tenderAmount: number;
  lines: OutboxReceiptLine[];
  createdAt: number;
  customerId?: string;
  customerName?: string;
  facturaRfc?: string;
  facturaUsoCfdi?: string;
};

export type PosAuditEvent = {
  id: string;
  timestamp: number;
  cashierId: string;
  eventType: PosAuditEventType;
  orderId: string;
  productId?: string;
  valueVoided: number;
  managerOverrideId?: string;
};

export class PosDatabase extends Dexie {
  catalog_cache!: Table<CatalogCacheRow, string>;
  cart_drafts!: Table<CartDraftRow, string>;
  outbox_receipts!: Table<OutboxReceiptRow, string>;
  audit_events!: Table<PosAuditEvent, string>;

  constructor(name = 'invsys-pos') {
    super(name);
    this.version(1).stores({
      catalog_cache: 'id, upc, name',
      cart_drafts: 'id, updatedAt',
      outbox_receipts: 'id, createdAt, storeLocationId',
    });
    this.version(2).stores({
      catalog_cache: 'id, upc, name',
      cart_drafts: 'id, updatedAt',
      outbox_receipts: 'id, createdAt, storeLocationId',
      audit_events: 'id, timestamp, eventType, orderId',
    });
    this.version(3).stores({
      catalog_cache: 'id, upc, name',
      cart_drafts: 'id, updatedAt',
      outbox_receipts: 'id, createdAt, storeLocationId',
      audit_events: 'id, timestamp, eventType, orderId',
    });
  }
}

export const db = new PosDatabase();

export const ACTIVE_CART_ID = 'active';

export async function saveCartDraft(lines: CartLine[], orderId?: string): Promise<void> {
  await db.cart_drafts.put({ id: ACTIVE_CART_ID, lines, orderId, updatedAt: Date.now() });
}

export async function loadCartDraft(): Promise<CartLine[]> {
  const draft = await db.cart_drafts.get(ACTIVE_CART_ID);
  return draft?.lines ?? [];
}

export async function loadActiveOrderId(): Promise<string | undefined> {
  const draft = await db.cart_drafts.get(ACTIVE_CART_ID);
  return draft?.orderId;
}

export async function clearCartDraft(): Promise<void> {
  await db.cart_drafts.delete(ACTIVE_CART_ID);
}

export async function enqueueReceipt(row: OutboxReceiptRow): Promise<void> {
  await db.outbox_receipts.put(row);
}

export async function deleteOutboxReceipts(ids: string[]): Promise<void> {
  if (ids.length === 0) return;
  await db.outbox_receipts.bulkDelete(ids);
}

export async function logPosEvent(event: Omit<PosAuditEvent, 'id'>): Promise<PosAuditEvent> {
  const row: PosAuditEvent = { ...event, id: uuidv7() };
  await db.audit_events.put(row);
  return row;
}

export async function deleteAuditEvents(ids: string[]): Promise<void> {
  if (ids.length === 0) return;
  await db.audit_events.bulkDelete(ids);
}

export function lookupCatalog(upc: string): Promise<CatalogCacheRow | undefined> {
  const key = upc.trim();
  if (!key) return Promise.resolve(undefined);
  return db.catalog_cache.where('upc').equals(key).first();
}

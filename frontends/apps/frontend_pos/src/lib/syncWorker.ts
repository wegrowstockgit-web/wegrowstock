import { db, deleteOutboxReceipts, type OutboxReceiptRow } from './db';

export type SyncResult = {
  flushed: number;
  skipped: boolean;
  error?: string;
};

const DEFAULT_STORE_ID = 'a0000000-0000-4000-8000-000000000601';

export function toSyncPayload(rows: OutboxReceiptRow[]) {
  return rows.map((row) => ({
    id: row.id,
    storeLocationId: row.storeLocationId || DEFAULT_STORE_ID,
    tenderType: row.tenderType,
    taxRegion: row.taxRegion,
    lines: row.lines.map((line) => ({
      variantId: line.variantId,
      upc: line.upc,
      quantity: line.quantity,
      unitPrice: line.unitPrice,
    })),
  }));
}

export async function flushOutbox(
  fetchImpl: typeof fetch = fetch,
  online: boolean = typeof navigator !== 'undefined' ? navigator.onLine : true,
): Promise<SyncResult> {
  if (!online) {
    return { flushed: 0, skipped: true };
  }
  const pending = await db.outbox_receipts.orderBy('createdAt').toArray();
  if (pending.length === 0) {
    return { flushed: 0, skipped: false };
  }

  const response = await fetchImpl('/api/v1/pos/sync-receipts', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(toSyncPayload(pending)),
  });

  if (!response.ok) {
    return { flushed: 0, skipped: false, error: `HTTP ${response.status}` };
  }

  const body = (await response.json()) as {
    accepted?: number;
    duplicates?: number;
    rejected?: Array<{ receiptId?: string }>;
  };
  const rejected = new Set((body.rejected ?? []).map((row) => row.receiptId).filter(Boolean));
  const done = pending.filter((row) => !rejected.has(row.id)).map((row) => row.id);
  await deleteOutboxReceipts(done);
  return { flushed: done.length, skipped: false };
}

export function startOutboxPolling(intervalMs = 4000, flush = flushOutbox): () => void {
  let stopped = false;
  const tick = () => {
    if (stopped) return;
    void flush().catch(() => undefined);
  };
  tick();
  const handle = window.setInterval(tick, intervalMs);
  const onOnline = () => tick();
  window.addEventListener('online', onOnline);
  return () => {
    stopped = true;
    window.clearInterval(handle);
    window.removeEventListener('online', onOnline);
  };
}

import { db, deleteAuditEvents, deleteOutboxReceipts, type OutboxReceiptRow, type PosAuditEvent } from './db';

export type SyncResult = {
  flushed: number;
  skipped: boolean;
  error?: string;
  auditFlushed: number;
  auditError?: string;
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

export function toAuditSyncPayload(rows: PosAuditEvent[]) {
  return rows.map((row) => ({
    id: row.id,
    timestamp: row.timestamp,
    cashierId: row.cashierId,
    eventType: row.eventType,
    orderId: row.orderId,
    productId: row.productId,
    valueVoided: row.valueVoided,
    managerOverrideId: row.managerOverrideId,
  }));
}

async function flushReceipts(
  fetchImpl: typeof fetch,
): Promise<Pick<SyncResult, 'flushed' | 'error'>> {
  const pending = await db.outbox_receipts.orderBy('createdAt').toArray();
  if (pending.length === 0) {
    return { flushed: 0 };
  }

  const response = await fetchImpl('/api/v1/pos/sync-receipts', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(toSyncPayload(pending)),
  });

  if (!response.ok) {
    return { flushed: 0, error: `HTTP ${response.status}` };
  }

  const body = (await response.json()) as {
    accepted?: number;
    duplicates?: number;
    rejected?: Array<{ receiptId?: string }>;
  };
  const rejected = new Set((body.rejected ?? []).map((row) => row.receiptId).filter(Boolean));
  const done = pending.filter((row) => !rejected.has(row.id)).map((row) => row.id);
  await deleteOutboxReceipts(done);
  return { flushed: done.length };
}

export async function flushAuditEvents(
  fetchImpl: typeof fetch = fetch,
  online: boolean = typeof navigator !== 'undefined' ? navigator.onLine : true,
): Promise<Pick<SyncResult, 'auditFlushed' | 'auditError' | 'skipped'>> {
  if (!online) {
    return { auditFlushed: 0, skipped: true };
  }
  const pending = await db.audit_events.orderBy('timestamp').toArray();
  if (pending.length === 0) {
    return { auditFlushed: 0, skipped: false };
  }

  const response = await fetchImpl('/api/v1/pos/audit-sync', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(toAuditSyncPayload(pending)),
  });

  if (!response.ok) {
    return { auditFlushed: 0, skipped: false, auditError: `HTTP ${response.status}` };
  }

  const body = (await response.json()) as {
    accepted?: number;
    duplicates?: number;
    rejected?: Array<{ eventId?: string }>;
  };
  const rejected = new Set((body.rejected ?? []).map((row) => row.eventId).filter(Boolean));
  const done = pending.filter((row) => !rejected.has(row.id)).map((row) => row.id);
  await deleteAuditEvents(done);
  return { auditFlushed: done.length, skipped: false };
}

export async function flushOutbox(
  fetchImpl: typeof fetch = fetch,
  online: boolean = typeof navigator !== 'undefined' ? navigator.onLine : true,
): Promise<SyncResult> {
  if (!online) {
    return { flushed: 0, skipped: true, auditFlushed: 0 };
  }

  let receipts: Pick<SyncResult, 'flushed' | 'error'> = { flushed: 0 };
  try {
    receipts = await flushReceipts(fetchImpl);
  } catch (error) {
    receipts = { flushed: 0, error: error instanceof Error ? error.message : 'receipt-sync-failed' };
  }

  let audits: Pick<SyncResult, 'auditFlushed' | 'auditError'> = { auditFlushed: 0 };
  try {
    audits = await flushAuditEvents(fetchImpl, true);
  } catch (error) {
    audits = {
      auditFlushed: 0,
      auditError: error instanceof Error ? error.message : 'audit-sync-failed',
    };
  }

  return {
    flushed: receipts.flushed,
    skipped: false,
    error: receipts.error,
    auditFlushed: audits.auditFlushed,
    auditError: audits.auditError,
  };
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

import { lookupPosVariantByUpc } from '@/api/client';
import { db, type PosProduct } from '@/lib/db';

export const UNKNOWN_UPC = 'Unknown UPC';
export const UNKNOWN_UPC_OFFLINE = 'Unknown UPC (Offline)';

function isOffline(): boolean {
  return typeof navigator !== 'undefined' && navigator.onLine === false;
}

export async function lookupScannedUpc(
  scannedUpc: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PosProduct> {
  const upc = scannedUpc.trim();
  if (!upc) {
    throw new Error(UNKNOWN_UPC);
  }

  const cached = await db.products.where('upc').equals(upc).first();
  if (cached) {
    return cached;
  }

  if (isOffline()) {
    throw new Error(UNKNOWN_UPC_OFFLINE);
  }

  try {
    const item = await lookupPosVariantByUpc(upc, fetchImpl);
    await db.products.put(item);
    return item;
  } catch {
    throw new Error(UNKNOWN_UPC);
  }
}

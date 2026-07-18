/**
 * Compatibility facade over {@link parseGs1} for callers that expect Gs1Elements.
 * Canonical offline/UI parser lives in `frontend/src/utils/gs1Parser.ts`.
 */
import { parseGs1 } from '@/utils/gs1Parser';

export interface Gs1Elements {
  raw: string;
  gtin: string | null;
  lot: string | null;
  expiry: string | null;
  serial: string | null;
  variableQuantity: number | null;
  all: Record<string, string>;
}

export function parseGs1Barcode(barcode: string | null | undefined): Gs1Elements | null {
  const parsed = parseGs1(barcode);
  if (!parsed.isGs1) return null;
  const all: Record<string, string> = {};
  if (parsed.sku && parsed.sku !== parsed.raw) all['01'] = parsed.sku;
  if (parsed.lotNumber) all['10'] = parsed.lotNumber;
  if (parsed.serialNumber) all['21'] = parsed.serialNumber;
  if (parsed.expiryDate) {
    // Rebuild YYMMDD for element map parity with backend
    const [y, m, d] = parsed.expiryDate.split('-');
    all['17'] = `${y.slice(2)}${m}${d}`;
  }
  if (parsed.quantity != null) all['30'] = String(parsed.quantity);
  return {
    raw: parsed.raw ?? String(barcode).trim(),
    gtin: parsed.sku || null,
    lot: parsed.lotNumber ?? null,
    expiry: parsed.expiryDate ?? null,
    serial: parsed.serialNumber ?? null,
    variableQuantity: parsed.quantity ?? null,
    all,
  };
}

export function gs1LookupKey(barcode: string): string {
  return parseGs1(barcode).sku;
}

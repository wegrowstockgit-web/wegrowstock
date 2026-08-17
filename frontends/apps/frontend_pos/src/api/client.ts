import type { PosProduct } from '@/lib/db';

export type PosCatalogItemDto = {
  variantId?: string;
  upc?: string;
  sku?: string | null;
  name?: string | null;
  retailPrice?: number | string | null;
  imageUrl?: string | null;
};

export function mapCatalogItem(raw: PosCatalogItemDto | null | undefined): PosProduct | null {
  if (!raw || raw.variantId == null || raw.upc == null) return null;
  const id = String(raw.variantId).trim();
  const upc = String(raw.upc).trim();
  const price = Number(raw.retailPrice);
  if (!id || !upc || !Number.isFinite(price)) return null;
  const sku = raw.sku == null ? '' : String(raw.sku);
  const name = String(raw.name || sku || upc).trim() || upc;
  const imageUrl = raw.imageUrl ? String(raw.imageUrl) : undefined;
  return { id, upc, sku, name, price, imageUrl };
}

export function mapCatalogItems(payload: unknown): PosProduct[] {
  if (!Array.isArray(payload)) return [];
  return payload
    .map((row) => mapCatalogItem(row as PosCatalogItemDto))
    .filter((row): row is PosProduct => row != null);
}

export async function lookupPosVariantByUpc(
  upc: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PosProduct> {
  const key = upc.trim();
  const response = await fetchImpl(`/api/v1/pos/catalog/lookup?${new URLSearchParams({ upc: key })}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const item = mapCatalogItem((await response.json()) as PosCatalogItemDto);
  if (!item) {
    throw new Error('Unknown UPC');
  }
  return item;
}

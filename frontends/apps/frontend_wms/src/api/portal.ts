import type { PortalCatalogItem } from '@/api/types';

/** Raw shape from GET /api/v1/portal/catalog */
export interface PortalCatalogItemRaw {
  variantId: string;
  productId: string;
  sku: string;
  productName: string;
  unitPrice: number;
  currency: string;
  primaryMediaUrl?: string | null;
}

export function mapPortalCatalogItem(raw: PortalCatalogItemRaw): PortalCatalogItem {
  return {
    id: raw.variantId,
    sku: raw.sku,
    name: raw.productName,
    unitPrice: Number(raw.unitPrice),
    currency: raw.currency,
    primaryMediaUrl: raw.primaryMediaUrl ?? null,
  };
}

export function mapPortalCatalog(items: PortalCatalogItemRaw[]): PortalCatalogItem[] {
  return items.map(mapPortalCatalogItem);
}

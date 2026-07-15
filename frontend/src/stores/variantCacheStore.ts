import { create } from 'zustand';

/** Minimal variant facts needed for floor GS1 graceful degradation. */
export interface CachedVariant {
  id: string;
  sku: string;
  barcode?: string | null;
  isLotTracked: boolean;
}

interface VariantCacheState {
  byKey: Record<string, CachedVariant>;
  upsert: (variant: CachedVariant) => void;
  upsertMany: (variants: CachedVariant[]) => void;
  lookup: (key: string | null | undefined) => CachedVariant | undefined;
  clear: () => void;
}

function indexKeys(variant: CachedVariant): string[] {
  const keys = [variant.id, variant.sku];
  if (variant.barcode?.trim()) keys.push(variant.barcode.trim());
  return keys.map((k) => k.toLowerCase());
}

/**
 * Zustand cache of variant lot-tracking flags for offline/instant GS1 UX.
 * Populated from catalog fetches and fulfillment scan responses.
 */
export const useVariantCacheStore = create<VariantCacheState>((set, get) => ({
  byKey: {},
  upsert: (variant) => {
    set((state) => {
      const next = { ...state.byKey };
      for (const key of indexKeys(variant)) {
        next[key] = variant;
      }
      return { byKey: next };
    });
  },
  upsertMany: (variants) => {
    set((state) => {
      const next = { ...state.byKey };
      for (const variant of variants) {
        for (const key of indexKeys(variant)) {
          next[key] = variant;
        }
      }
      return { byKey: next };
    });
  },
  lookup: (key) => {
    if (!key?.trim()) return undefined;
    return get().byKey[key.trim().toLowerCase()];
  },
  clear: () => set({ byKey: {} }),
}));

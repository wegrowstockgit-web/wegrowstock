import { afterEach, describe, expect, it, vi } from 'vitest';
import { lookupPosVariantByUpc } from '@/api/client';
import { db } from '@/lib/db';
import { lookupScannedUpc, UNKNOWN_UPC, UNKNOWN_UPC_OFFLINE } from './useBarcodeScanner';

describe('lookupScannedUpc', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns a cached product without hitting the network', async () => {
    await db.products.put({
      id: 'v1',
      upc: '7501234567890',
      sku: 'AGUA',
      name: 'Agua 600ml',
      price: 12.5,
    });
    const fetchImpl = vi.fn();
    const item = await lookupScannedUpc('7501234567890', fetchImpl as unknown as typeof fetch);
    expect(item.name).toBe('Agua 600ml');
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('throws Unknown UPC (Offline) when the cache misses and the register is offline', async () => {
    vi.stubGlobal('navigator', { ...navigator, onLine: false });
    await expect(lookupScannedUpc('000')).rejects.toThrow(UNKNOWN_UPC_OFFLINE);
  });

  it('caches a live lookup and returns it', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        variantId: 'v-new',
        upc: '111111111111',
        sku: 'NEW',
        name: 'New SKU',
        retailPrice: 7.25,
        imageUrl: '/img.png',
      }),
    });
    const item = await lookupScannedUpc('111111111111', fetchImpl as unknown as typeof fetch);
    expect(item).toMatchObject({ id: 'v-new', name: 'New SKU', price: 7.25 });
    expect(await db.products.get('v-new')).toMatchObject({ upc: '111111111111' });
  });

  it('throws Unknown UPC when the live lookup fails', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({ ok: false, status: 404 });
    await expect(lookupScannedUpc('000', fetchImpl as unknown as typeof fetch)).rejects.toThrow(UNKNOWN_UPC);
  });
});

describe('lookupPosVariantByUpc', () => {
  it('maps the catalog DTO', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        variantId: 'v1',
        upc: '7501234567890',
        sku: 'AGUA',
        name: 'Agua 600ml',
        retailPrice: '12.50',
      }),
    });
    await expect(lookupPosVariantByUpc('7501234567890', fetchImpl as unknown as typeof fetch)).resolves.toMatchObject({
      id: 'v1',
      price: 12.5,
    });
    expect(String(fetchImpl.mock.calls[0]?.[0])).toContain('/api/v1/pos/catalog/lookup');
  });
});

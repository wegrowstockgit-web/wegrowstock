import { describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/api/client';
import {
  approveMeshConnection,
  draftPoPath,
  fetchMeshDiscover,
  fetchMeshNetwork,
  fetchMeshSharedCatalog,
  fetchMeshSourcingSuggestions,
  requestMeshConnection,
  updateMeshListing,
} from './mesh';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('mesh api', () => {
  it('loads discover, network, and shared catalog', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: [{ variantId: 'v1', productName: 'Widget', sellerName: 'Acme', sellerTenantId: 't2' }] });
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: [{ id: 'c1', displayStatus: 'REQUESTED', canApprove: false }] });
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: [{ variantId: 'v1', sku: 'SKU-1', published: false }] });

    await expect(fetchMeshDiscover()).resolves.toEqual([
      expect.objectContaining({ productName: 'Widget', sellerName: 'Acme' }),
    ]);
    await expect(fetchMeshNetwork()).resolves.toEqual([expect.objectContaining({ id: 'c1' })]);
    await expect(fetchMeshSharedCatalog()).resolves.toEqual([expect.objectContaining({ sku: 'SKU-1' })]);
  });

  it('requests and approves connections and updates listings', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: { id: 'c1', connectionStatus: 'REQUESTED' } });
    vi.mocked(apiClient.put).mockResolvedValue({ data: { variantId: 'v1', published: true, meshWholesalePrice: 9 } });

    await requestMeshConnection({ variantId: 'v1' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/mesh/connections/request', { variantId: 'v1' });

    vi.mocked(apiClient.post).mockResolvedValue({ data: { id: 'c1', connectionStatus: 'CONNECTED' } });
    await approveMeshConnection('c1');
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/mesh/connections/c1/approve');

    await updateMeshListing('v1', true, 9);
    expect(apiClient.put).toHaveBeenCalledWith('/api/v1/mesh/catalog/v1', {
      published: true,
      meshWholesalePrice: 9,
    });
  });

  it('builds a draft PO path and loads sourcing suggestions', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{ productName: 'Widget', partnerName: 'Acme', meshPartnerSku: 'SKU-1', supplierId: 'sup-1' }],
    });
    await expect(fetchMeshSourcingSuggestions()).resolves.toHaveLength(1);
    expect(draftPoPath({ meshPartnerSku: 'SKU-1', supplierId: 'sup-1' })).toBe(
      '/purchase-orders/new?meshPartnerSku=SKU-1&supplierId=sup-1',
    );
  });
});

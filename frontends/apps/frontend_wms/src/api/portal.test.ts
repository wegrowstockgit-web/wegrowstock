import { describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/api/client';
import {
  applyForWholesale,
  approveWholesaleApplication,
  fetchPublicShowroomCatalog,
  listWholesaleApplications,
  mapPortalCatalog,
  requestShowroomMagicLink,
} from './portal';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('portal catalog mapper', () => {
  it('maps variant id onto catalog item id', () => {
    const items = mapPortalCatalog([
      {
        variantId: 'v1',
        productId: 'p1',
        sku: 'SKU-1',
        productName: 'Widget',
        unitPrice: 12,
        currency: 'USD',
        primaryMediaUrl: null,
      },
    ]);
    expect(items[0]).toMatchObject({ id: 'v1', name: 'Widget', unitPrice: 12 });
  });

  it('posts a wholesale application', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { id: 'app-1', status: 'PENDING', companyName: 'Acme', email: 'a@b.co' },
    });
    await applyForWholesale({
      companyName: 'Acme',
      taxId: '12-3',
      contactName: 'Ada',
      email: 'a@b.co',
    });
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/showroom/apply',
      expect.objectContaining({ companyName: 'Acme', email: 'a@b.co' }),
      expect.objectContaining({ headers: expect.any(Object) }),
    );
  });

  it('lists and approves pending applications', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [{ id: 'app-1', status: 'PENDING' }] });
    vi.mocked(apiClient.post).mockResolvedValue({ data: { id: 'app-1', status: 'APPROVED' } });
    await expect(listWholesaleApplications()).resolves.toEqual([{ id: 'app-1', status: 'PENDING' }]);
    await expect(approveWholesaleApplication('app-1')).resolves.toMatchObject({ status: 'APPROVED' });
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/customers/applications/app-1/approve');
  });

  it('loads the public guest catalog and requests a magic link', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [
        {
          variantId: 'v1',
          productId: 'p1',
          sku: 'SKU-1',
          productName: 'Widget',
          unitPrice: 12,
          currency: 'USD',
        },
      ],
    });
    vi.mocked(apiClient.post).mockResolvedValue({ data: { status: 'accepted' } });
    const items = await fetchPublicShowroomCatalog();
    expect(items[0].id).toBe('v1');
    await expect(requestShowroomMagicLink('buyer@acme.test')).resolves.toEqual({ status: 'accepted' });
  });
});

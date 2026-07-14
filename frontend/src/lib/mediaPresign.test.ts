import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/api/client';
import { uploadViaPresign } from '@/lib/mediaPresign';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('uploadViaPresign', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200 }),
    );
  });

  it('presigns, PUTs to storage, then completes', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        objectKey: 'tenant/product/a.png',
        uploadUrl: 'http://localhost:9000/invsys-media/tenant/product/a.png?X-Amz-Signature=x',
        contentType: 'image/png',
        type: 'PRODUCT',
        expiresInSeconds: 600,
      },
    } as never);
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        id: 'media-1',
        contentUrl: '/api/v1/media/media-1/content',
        contentType: 'image/png',
        byteSize: 68,
      },
    } as never);

    const file = new File([new Uint8Array([1, 2, 3])], 'sku.png', { type: 'image/png' });
    const result = await uploadViaPresign(file, 'PRODUCT');

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/media/presign-upload', {
      params: { type: 'PRODUCT', filename: 'sku.png', contentType: 'image/png' },
    });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('localhost:9000'),
      expect.objectContaining({ method: 'PUT' }),
    );
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/media/complete', {
      objectKey: 'tenant/product/a.png',
      contentType: 'image/png',
    });
    expect(result.contentUrl).toBe('/api/v1/media/media-1/content');
  });
});

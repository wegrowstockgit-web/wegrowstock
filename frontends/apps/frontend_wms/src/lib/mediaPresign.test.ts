import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/api/client';
import { uploadViaPresign } from '@/lib/mediaPresign';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@/utils/imageCompression', () => ({
  compressImageForUpload: vi.fn(async (file: File) => file),
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
        objectKey: 'tenant/product/a.webp',
        uploadUrl: 'http://localhost:9000/invsys-media/tenant/product/a.webp?X-Amz-Signature=x',
        contentType: 'image/webp',
        type: 'PRODUCT',
        expiresInSeconds: 600,
      },
    } as never);
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        id: 'media-1',
        contentUrl: '/api/v1/media/media-1/content',
        contentType: 'image/webp',
        byteSize: 68,
      },
    } as never);

    const file = new File([new Uint8Array([1, 2, 3])], 'sku.webp', { type: 'image/webp' });
    const result = await uploadViaPresign(file, 'PRODUCT', { compress: false });

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/media/presign-upload', {
      params: { type: 'PRODUCT', filename: 'sku.webp', contentType: 'image/webp' },
    });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('localhost:9000'),
      expect.objectContaining({ method: 'PUT' }),
    );
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/media/complete', {
      objectKey: 'tenant/product/a.webp',
      contentType: 'image/webp',
    });
    expect(result.contentUrl).toBe('/api/v1/media/media-1/content');
  });

  it('compresses by default before pre-sign', async () => {
    const { compressImageForUpload } = await import('@/utils/imageCompression');
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        objectKey: 'tenant/product/b.webp',
        uploadUrl: 'http://localhost:9000/b',
        contentType: 'image/webp',
        type: 'PRODUCT',
        expiresInSeconds: 600,
      },
    } as never);
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        id: 'media-2',
        contentUrl: '/api/v1/media/media-2/content',
        contentType: 'image/webp',
        byteSize: 10,
      },
    } as never);

    const file = new File([new Uint8Array([9])], 'raw.png', { type: 'image/png' });
    await uploadViaPresign(file, 'PRODUCT');
    expect(compressImageForUpload).toHaveBeenCalledWith(file, { avatar: false });
  });
});

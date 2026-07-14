import { apiClient } from '@/api/client';

export type PresignType = 'PRODUCT' | 'TRANSACTION' | 'USER_AVATAR';

export interface PresignedUpload {
  objectKey: string;
  uploadUrl: string;
  contentType: string;
  type: string;
  expiresInSeconds: number;
}

export interface CompletedMedia {
  id: string;
  contentUrl: string;
  contentType: string;
  byteSize: number;
  originalFilename?: string | null;
}

/** Pre-sign → PUT bytes to MinIO/S3 → register MediaObject via /complete. */
export async function uploadViaPresign(
  file: File,
  type: PresignType,
): Promise<CompletedMedia> {
  const contentType = file.type || 'image/jpeg';
  const { data: presign } = await apiClient.get<PresignedUpload>('/api/v1/media/presign-upload', {
    params: {
      type,
      filename: file.name || 'capture.jpg',
      contentType,
    },
  });

  const putRes = await fetch(presign.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': presign.contentType },
    body: file,
  });
  if (!putRes.ok) {
    throw new Error(`Direct storage upload failed (${putRes.status})`);
  }

  const { data } = await apiClient.post<CompletedMedia>('/api/v1/media/complete', {
    objectKey: presign.objectKey,
    contentType: presign.contentType,
  });
  return data;
}

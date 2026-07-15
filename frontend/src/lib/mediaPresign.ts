import { apiClient } from '@/api/client';
import { compressImageForUpload } from '@/utils/imageCompression';

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

export interface UploadViaPresignOptions {
  /** Compress client-side before PUT (default true). */
  compress?: boolean;
  /** Called when local compression starts / finishes (for loading copy). */
  onPhase?: (phase: 'compressing' | 'uploading') => void;
}

/** Pre-sign → PUT bytes to MinIO/S3 → register MediaObject via /complete. */
export async function uploadViaPresign(
  file: File,
  type: PresignType,
  options: UploadViaPresignOptions = {},
): Promise<CompletedMedia> {
  const shouldCompress = options.compress !== false;
  let payload = file;
  if (shouldCompress) {
    options.onPhase?.('compressing');
    payload = await compressImageForUpload(file, {
      avatar: type === 'USER_AVATAR',
    });
  }

  options.onPhase?.('uploading');
  const contentType = payload.type || 'image/webp';
  const { data: presign } = await apiClient.get<PresignedUpload>('/api/v1/media/presign-upload', {
    params: {
      type,
      filename: payload.name || 'upload.webp',
      contentType,
    },
  });

  const putRes = await fetch(presign.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': presign.contentType },
    body: payload,
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

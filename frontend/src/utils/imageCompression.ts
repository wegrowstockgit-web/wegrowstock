/** Max edge length for catalog / floor / evidence uploads. */
export const DEFAULT_MAX_DIMENSION = 1024;

/** Soft target payload for weak warehouse Wi-Fi (~200KB). */
export const DEFAULT_MAX_BYTES = 200_000;

/** Tighter avatar budget for header/profile thumbnails. */
export const AVATAR_MAX_DIMENSION = 512;
export const AVATAR_MAX_BYTES = 80_000;

export interface CompressImageOptions {
  maxDimension?: number;
  maxBytes?: number;
  /** Initial WebP quality (0–1). Reduced automatically to meet maxBytes. */
  quality?: number;
  /** Avatar preset: smaller dimensions + lower quality floor. */
  avatar?: boolean;
}

export function fitWithin(
  width: number,
  height: number,
  maxDimension: number,
): { width: number; height: number } {
  if (width <= 0 || height <= 0) {
    return { width: 1, height: 1 };
  }
  const longest = Math.max(width, height);
  if (longest <= maxDimension) {
    return { width, height };
  }
  const scale = maxDimension / longest;
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error('Image encoding failed'));
      },
      type,
      quality,
    );
  });
}

async function encodeWebpPreferring(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  try {
    const webp = await canvasToBlob(canvas, 'image/webp', quality);
    if (webp.type === 'image/webp' || webp.size > 0) {
      // Some engines report empty type; sniff via size + requested mime.
      if (webp.size > 0) return webp.type ? webp : new Blob([webp], { type: 'image/webp' });
    }
  } catch {
    // fall through to JPEG
  }
  const jpeg = await canvasToBlob(canvas, 'image/jpeg', quality);
  return jpeg;
}

/**
 * Client-side resize + WebP encode for MinIO pre-signed uploads.
 * Caps resolution at 1024² (512² for avatars) and targets ~200KB payloads.
 */
export async function compressImageForUpload(
  file: File,
  options: CompressImageOptions = {},
): Promise<File> {
  if (!file.type.startsWith('image/') && file.type !== '') {
    return file;
  }

  const avatar = options.avatar === true;
  const maxDimension =
    options.maxDimension ?? (avatar ? AVATAR_MAX_DIMENSION : DEFAULT_MAX_DIMENSION);
  const maxBytes = options.maxBytes ?? (avatar ? AVATAR_MAX_BYTES : DEFAULT_MAX_BYTES);
  let quality = options.quality ?? (avatar ? 0.72 : 0.82);
  const qualityFloor = avatar ? 0.4 : 0.45;

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    // Non-decodable image — let the server reject if needed.
    return file;
  }

  try {
    let { width, height } = fitWithin(bitmap.width, bitmap.height, maxDimension);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d', { alpha: false });
    if (!ctx) {
      return file;
    }
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(bitmap, 0, 0, width, height);

    let blob = await encodeWebpPreferring(canvas, quality);
    while (blob.size > maxBytes && quality > qualityFloor) {
      quality = Math.max(qualityFloor, quality - 0.08);
      blob = await encodeWebpPreferring(canvas, quality);
    }

    // Still oversized: shrink canvas further and re-encode.
    let guard = 0;
    while (blob.size > maxBytes && Math.max(width, height) > 320 && guard < 4) {
      guard += 1;
      width = Math.max(1, Math.round(width * 0.75));
      height = Math.max(1, Math.round(height * 0.75));
      canvas.width = width;
      canvas.height = height;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, width, height);
      ctx.drawImage(bitmap, 0, 0, width, height);
      blob = await encodeWebpPreferring(canvas, quality);
    }

    const mime = blob.type || 'image/webp';
    const ext = mime.includes('jpeg') || mime.includes('jpg') ? 'jpg' : 'webp';
    const base = (file.name || 'upload').replace(/\.[^.]+$/i, '').replace(/[^\w.-]+/g, '_') || 'upload';
    return new File([blob], `${base}.${ext}`, {
      type: mime.startsWith('image/') ? mime : 'image/webp',
      lastModified: Date.now(),
    });
  } finally {
    bitmap.close();
  }
}

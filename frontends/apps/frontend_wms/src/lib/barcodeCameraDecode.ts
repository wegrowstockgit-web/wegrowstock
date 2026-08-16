const BARCODE_FORMATS = [
  'qr_code',
  'code_128',
  'code_39',
  'ean_13',
  'ean_8',
  'upc_a',
  'upc_e',
  'itf',
  'codabar',
  'data_matrix',
] as const;

type BarcodeDetectorLike = {
  detect: (source: CanvasImageSource) => Promise<Array<{ rawValue?: string }>>;
};

type BarcodeDetectorCtor = new (options: { formats: string[] }) => BarcodeDetectorLike;

function getBarcodeDetector(): BarcodeDetectorCtor | undefined {
  const ctor = (globalThis as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;
  return typeof ctor === 'function' ? ctor : undefined;
}

function grabFrame(video: HTMLVideoElement): HTMLCanvasElement | null {
  if (video.videoWidth === 0 || video.videoHeight === 0) return null;
  const canvas = document.createElement('canvas');
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) return null;
  ctx.drawImage(video, 0, 0);
  return canvas;
}

function toPackedRgb(imageData: ImageData): Int32Array {
  const { data, width, height } = imageData;
  const pixels = new Int32Array(width * height);
  for (let i = 0, p = 0; i < data.length; i += 4, p += 1) {
    pixels[p] = (data[i] << 16) | (data[i + 1] << 8) | data[i + 2];
  }
  return pixels;
}

async function decodeWithZxing(canvas: HTMLCanvasElement): Promise<string | null> {
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx || canvas.width === 0 || canvas.height === 0) return null;
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
  try {
    const { BinaryBitmap, HybridBinarizer, MultiFormatReader, RGBLuminanceSource } =
      await import('@zxing/library');
    const source = new RGBLuminanceSource(toPackedRgb(imageData), canvas.width, canvas.height);
    const bitmap = new BinaryBitmap(new HybridBinarizer(source));
    const text = new MultiFormatReader().decode(bitmap).getText()?.trim();
    return text || null;
  } catch {
    return null;
  }
}

/**
 * Decode a barcode from a live video frame. Prefers the native BarcodeDetector
 * API (Chromium) and falls back to ZXing for Safari / iPad WebViews.
 */
export async function decodeBarcodeFromVideo(video: HTMLVideoElement): Promise<string | null> {
  if (video.readyState < 2 || video.videoWidth === 0) {
    return null;
  }

  const Detector = getBarcodeDetector();
  if (Detector) {
    try {
      const detector = new Detector({ formats: [...BARCODE_FORMATS] });
      const codes = await detector.detect(video);
      const value = codes.find((code) => code.rawValue?.trim())?.rawValue?.trim();
      if (value) return value;
    } catch {
      // Native detector missing a format — try ZXing.
    }
  }

  const canvas = grabFrame(video);
  if (!canvas) return null;
  return decodeWithZxing(canvas);
}

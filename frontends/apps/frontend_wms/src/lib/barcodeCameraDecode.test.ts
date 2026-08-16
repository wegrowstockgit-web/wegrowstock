import { afterEach, describe, expect, it, vi } from 'vitest';
import { decodeBarcodeFromVideo } from './barcodeCameraDecode';

const decode = vi.fn();

vi.mock('@zxing/library', () => ({
  BinaryBitmap: class {
    constructor(_binarizer: unknown) {}
  },
  HybridBinarizer: class {
    constructor(_source: unknown) {}
  },
  RGBLuminanceSource: class {
    constructor(_pixels: Int32Array, _width: number, _height: number) {}
  },
  MultiFormatReader: class {
    decode() {
      return decode();
    }
  },
}));

function videoStub(readyState = 2, width = 4): HTMLVideoElement {
  return {
    readyState,
    videoWidth: width,
    videoHeight: 4,
  } as HTMLVideoElement;
}

function stubCanvas() {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
    drawImage: vi.fn(),
    getImageData: () => ({
      data: new Uint8ClampedArray(4 * 4 * 4),
      width: 4,
      height: 4,
    }),
  } as unknown as CanvasRenderingContext2D);
}

describe('decodeBarcodeFromVideo', () => {
  afterEach(() => {
    delete (globalThis as { BarcodeDetector?: unknown }).BarcodeDetector;
    decode.mockReset();
    vi.restoreAllMocks();
  });

  it('returns null when the video has no frame yet', async () => {
    expect(await decodeBarcodeFromVideo(videoStub(0, 0))).toBeNull();
  });

  it('reads a native BarcodeDetector payload', async () => {
    const detect = vi.fn().mockResolvedValue([{ rawValue: '  SKU-CAM-1  ' }]);
    (globalThis as { BarcodeDetector?: unknown }).BarcodeDetector = class {
      detect = detect;
    };

    await expect(decodeBarcodeFromVideo(videoStub())).resolves.toBe('SKU-CAM-1');
    expect(detect).toHaveBeenCalled();
  });

  it('falls back to ZXing when BarcodeDetector is absent', async () => {
    stubCanvas();
    decode.mockReturnValue({ getText: () => 'ZXING-SKU' });
    await expect(decodeBarcodeFromVideo(videoStub())).resolves.toBe('ZXING-SKU');
  });

  it('returns null when ZXing cannot decode the frame', async () => {
    stubCanvas();
    decode.mockImplementation(() => {
      throw new Error('NotFoundException');
    });
    await expect(decodeBarcodeFromVideo(videoStub())).resolves.toBeNull();
  });
});

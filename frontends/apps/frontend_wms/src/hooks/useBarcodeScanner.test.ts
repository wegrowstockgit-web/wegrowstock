import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { extractIntentBarcode, useBarcodeScanner } from '@/hooks/useBarcodeScanner';

describe('extractIntentBarcode', () => {
  it('reads DataWedge data_string extras', () => {
    expect(
      extractIntentBarcode({
        extras: { 'com.symbol.datawedge.data_string': 'SKU-12345' },
      })
    ).toBe('SKU-12345');
  });

  it('reads Honeywell / Capacitor barcode fields', () => {
    expect(extractIntentBarcode({ barcode: 'LOT-99' })).toBe('LOT-99');
    expect(extractIntentBarcode({ data: '  PACK-1  ' })).toBe('PACK-1');
    expect(extractIntentBarcode('RAW-CODE')).toBe('RAW-CODE');
  });

  it('returns null for empty payloads', () => {
    expect(extractIntentBarcode(null)).toBeNull();
    expect(extractIntentBarcode({})).toBeNull();
    expect(extractIntentBarcode('   ')).toBeNull();
  });
});

describe('useBarcodeScanner scan event matrix', () => {
  it('emits ScanEventPayload with idempotencyKey and scannedAt on hardware scan', () => {
    const onScan = vi.fn();
    const onScanEvent = vi.fn();
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee');

    renderHook(() =>
      useBarcodeScanner({ enabled: true, captureAll: true, onScan, onScanEvent }),
    );

    act(() => {
      window.dispatchEvent(
        new CustomEvent('hardwareScan', { detail: { barcode: 'SKU-FLOOR-1' } }),
      );
    });

    expect(onScanEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        barcode: 'SKU-FLOOR-1',
        idempotencyKey: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
        scannedAt: expect.any(Number),
      }),
    );
    expect(onScan).toHaveBeenCalledWith('SKU-FLOOR-1', expect.anything());
  });
});

describe('useBarcodeScanner listener lifecycle', () => {
  it('removes the capture-phase keydown listener on unmount', () => {
    const onScan = vi.fn();
    const addSpy = vi.spyOn(window, 'addEventListener');
    const removeSpy = vi.spyOn(window, 'removeEventListener');

    const { unmount } = renderHook(() =>
      useBarcodeScanner({ enabled: true, captureAll: true, onScan }),
    );

    const keydownReg = addSpy.mock.calls.find(
      (call) => call[0] === 'keydown' && call[2] === true,
    );
    expect(keydownReg).toBeTruthy();
    const handler = keydownReg?.[1] as EventListener;

    unmount();

    expect(
      removeSpy.mock.calls.some(
        (call) => call[0] === 'keydown' && call[1] === handler && call[2] === true,
      ),
    ).toBe(true);

    act(() => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'A', bubbles: true }));
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });
    expect(onScan).not.toHaveBeenCalled();

    addSpy.mockRestore();
    removeSpy.mockRestore();
  });
});

describe('useBarcodeScanner camera + status', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'serial');
  });

  it('exposes UNSUPPORTED status, camera trigger, and interchangeable ingestScan', () => {
    const onScan = vi.fn();
    const { result } = renderHook(() =>
      useBarcodeScanner({ enabled: true, captureAll: true, onScan }),
    );

    expect(result.current.hardwareStatus).toBe('UNSUPPORTED');
    expect(result.current.triggerCamera).toBe(false);

    act(() => {
      result.current.setTriggerCamera(true);
    });
    expect(result.current.triggerCamera).toBe(true);

    act(() => {
      result.current.ingestScan('CAM-42', 'camera');
    });
    expect(onScan).toHaveBeenCalledWith('CAM-42', expect.anything());
    expect(result.current.hardwareStatus).toBe('UNSUPPORTED');
  });

  it('marks CONNECTED after a hardware ingest when Web Serial exists', () => {
    Object.defineProperty(navigator, 'serial', { configurable: true, value: {} });
    const onScan = vi.fn();
    const { result } = renderHook(() =>
      useBarcodeScanner({ enabled: true, captureAll: true, onScan }),
    );

    expect(result.current.hardwareStatus).toBe('DISCONNECTED');

    act(() => {
      window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: 'SKU-HW-1' } }));
    });

    expect(onScan).toHaveBeenCalledWith('SKU-HW-1', expect.anything());
    expect(result.current.hardwareStatus).toBe('CONNECTED');

    act(() => {
      result.current.ingestScan('CAM-KEEP', 'camera');
    });
    expect(result.current.hardwareStatus).toBe('CONNECTED');
  });

  it('does not treat camera ingest as a hardware connection', () => {
    Object.defineProperty(navigator, 'serial', { configurable: true, value: {} });
    const onScan = vi.fn();
    const { result } = renderHook(() =>
      useBarcodeScanner({ enabled: true, captureAll: true, onScan }),
    );

    expect(result.current.hardwareStatus).toBe('DISCONNECTED');
    act(() => {
      result.current.ingestScan('CAM-ONLY', 'camera');
    });
    expect(onScan).toHaveBeenCalledWith('CAM-ONLY', expect.anything());
    expect(result.current.hardwareStatus).toBe('DISCONNECTED');
  });
});

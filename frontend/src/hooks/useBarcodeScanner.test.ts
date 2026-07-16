import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
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

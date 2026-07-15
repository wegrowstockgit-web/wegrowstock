import { renderHook, act } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useBarcodeScanner } from '@/hooks/useBarcodeScanner';
import { useScanBufferStore } from '@/stores/scanBuffer';
import type { ParsedBarcode } from '@/utils/gs1Parser';

describe('useBarcodeScanner GS1 intercept', () => {
  beforeEach(() => {
    useScanBufferStore.setState({ buffer: '', lastScan: null, lastScanAt: null });
  });

  it('commits GTIN to buffer and emits onGs1Scan before onScan', () => {
    const onScan = vi.fn();
    const onGs1Scan = vi.fn();
    const order: string[] = [];

    renderHook(() =>
      useBarcodeScanner({
        enabled: true,
        captureAll: true,
        onGs1Scan: (parsed: ParsedBarcode) => {
          order.push('gs1');
          onGs1Scan(parsed);
        },
        onScan: (code, parsed) => {
          order.push('scan');
          onScan(code, parsed);
        },
      }),
    );

    // Intent/custom-event path (parentheses are unreliable via KeyboardEvent.key).
    act(() => {
      window.dispatchEvent(
        new CustomEvent('hardwareScan', {
          detail: { barcode: '(01)01234567890128(10)LOT9(17)251231(30)3' },
        }),
      );
    });

    expect(order).toEqual(['gs1', 'scan']);
    expect(onGs1Scan).toHaveBeenCalledWith(
      expect.objectContaining({
        isGs1: true,
        sku: '01234567890128',
        lotNumber: 'LOT9',
        expiryDate: '2025-12-31',
        quantity: 3,
      }),
    );
    expect(onScan).toHaveBeenCalledWith(
      '01234567890128',
      expect.objectContaining({ isGs1: true }),
    );
    expect(useScanBufferStore.getState().lastScan).toBe('01234567890128');
  });

  it('does not treat plain SKU as GS1', () => {
    const onGs1Scan = vi.fn();
    const onScan = vi.fn();

    renderHook(() =>
      useBarcodeScanner({
        enabled: true,
        captureAll: true,
        onGs1Scan,
        onScan,
      }),
    );

    act(() => {
      for (const key of 'SKU-PLAIN') {
        window.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
      }
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });

    expect(onGs1Scan).not.toHaveBeenCalled();
    expect(onScan).toHaveBeenCalledWith('SKU-PLAIN', expect.objectContaining({ isGs1: false }));
    expect(useScanBufferStore.getState().lastScan).toBe('SKU-PLAIN');
  });

  it('ingests DataWedge custom events and strips affixes', () => {
    const onScan = vi.fn();
    renderHook(() =>
      useBarcodeScanner({
        enabled: true,
        captureAll: true,
        prefix: ']',
        suffix: '!',
        onScan,
      }),
    );

    act(() => {
      window.dispatchEvent(
        new CustomEvent('datawedge', {
          detail: { barcode: ']8901000000001!' },
        }),
      );
    });

    expect(onScan).toHaveBeenCalledWith('8901000000001', expect.any(Object));
  });

  it('ingests window message barcode payloads', () => {
    const onScan = vi.fn();
    renderHook(() =>
      useBarcodeScanner({
        enabled: true,
        captureAll: true,
        onScan,
      }),
    );

    act(() => {
      window.dispatchEvent(new MessageEvent('message', { data: { data: 'MSG-SKU-1' } }));
    });

    expect(onScan).toHaveBeenCalledWith('MSG-SKU-1', expect.any(Object));
  });
});


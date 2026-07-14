import { useCallback, useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { useBarcodeScanner, type BarcodeScannerOptions } from '@/hooks/useBarcodeScanner';

/**
 * Unified scanner hook: native DataWedge-style intents on Capacitor,
 * HID keyboard wedge fallback on web.
 */
export function useHardwareScanner(options: BarcodeScannerOptions): void {
  const onScanRef = useRef(options.onScan);

  useEffect(() => {
    onScanRef.current = options.onScan;
  }, [options.onScan]);

  useBarcodeScanner(options);

  const handleNativeScan = useCallback((event: Event) => {
    const detail = (event as CustomEvent<{ barcode?: string }>).detail;
    const barcode = detail?.barcode?.trim();
    if (barcode) {
      onScanRef.current(barcode);
    }
  }, []);

  useEffect(() => {
    if (!Capacitor.isNativePlatform() || !options.enabled) {
      return;
    }
    window.addEventListener('hardwareScan', handleNativeScan);
    return () => window.removeEventListener('hardwareScan', handleNativeScan);
  }, [handleNativeScan, options.enabled]);
}

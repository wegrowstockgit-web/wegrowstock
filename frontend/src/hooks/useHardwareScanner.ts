import { useCallback, useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { useBarcodeScanner, type BarcodeScannerOptions } from '@/hooks/useBarcodeScanner';
import { useScanBufferStore } from '@/stores/scanBuffer';

/**
 * Extract barcode payload from enterprise scanner intent / custom-event shapes
 * (Zebra DataWedge, Honeywell Enterprise Browser, Capacitor bridges).
 */
export function extractIntentBarcode(detail: unknown): string | null {
  if (detail == null) return null;
  if (typeof detail === 'string') {
    const trimmed = detail.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
  if (typeof detail !== 'object') return null;
  const obj = detail as Record<string, unknown>;
  const candidates = [
    obj.barcode,
    obj.data,
    obj.scanData,
    obj.barcodedata,
    obj.barcodeData,
    obj.com_symbol_datawedge_api_data_string,
    obj['com.symbol.datawedge.data_string'],
    obj.dataString,
  ];
  for (const value of candidates) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }
  // Nested DataWedge intent extras
  const nested = obj as Record<string, unknown>;
  const extras = nested.extras ?? (nested.intent as Record<string, unknown> | undefined)?.extras;
  if (extras && typeof extras === 'object') {
    return extractIntentBarcode(extras);
  }
  return null;
}

const INTENT_EVENT_NAMES = [
  'hardwareScan',
  'datawedge_barcode',
  'datawedge',
  'BarcodeScanned',
  'honeywell.barcodeScanned',
  'scan',
] as const;

/**
 * Unified scanner hook: native DataWedge / Honeywell intent broadcasts on
 * Capacitor and PWA window events, plus HID keyboard wedge fallback.
 */
export function useHardwareScanner(options: BarcodeScannerOptions): void {
  const onScanRef = useRef(options.onScan);
  const { commit, reset } = useScanBufferStore();

  useEffect(() => {
    onScanRef.current = options.onScan;
  }, [options.onScan]);

  useBarcodeScanner(options);

  const ingest = useCallback(
    (raw: string | null) => {
      if (!raw) return;
      const barcode = raw.trim();
      if (!barcode) return;
      reset();
      commit(barcode);
      onScanRef.current(barcode);
    },
    [commit, reset]
  );

  const handleNativeScan = useCallback(
    (event: Event) => {
      const custom = event as CustomEvent<unknown>;
      ingest(extractIntentBarcode(custom.detail) ?? extractIntentBarcode((event as MessageEvent).data));
    },
    [ingest]
  );

  const handleMessage = useCallback(
    (event: MessageEvent) => {
      // Honeywell Enterprise Browser / WebView postMessage intent bridge
      ingest(extractIntentBarcode(event.data));
    },
    [ingest]
  );

  useEffect(() => {
    if (!options.enabled) {
      return;
    }
    // Window-level listeners work without input focus (kiosk / PWA).
    for (const name of INTENT_EVENT_NAMES) {
      window.addEventListener(name, handleNativeScan as EventListener);
    }
    window.addEventListener('message', handleMessage);
    // Capacitor native platforms still receive hardwareScan; web PWA gets DataWedge injects.
    if (Capacitor.isNativePlatform()) {
      // no-op: already listening for hardwareScan above
    }
    return () => {
      for (const name of INTENT_EVENT_NAMES) {
        window.removeEventListener(name, handleNativeScan as EventListener);
      }
      window.removeEventListener('message', handleMessage);
    };
  }, [handleNativeScan, handleMessage, options.enabled]);
}

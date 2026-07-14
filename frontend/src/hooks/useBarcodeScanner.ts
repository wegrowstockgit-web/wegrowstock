import { useCallback, useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { parseGs1Barcode } from '@/lib/gs1Barcode';

const SCANNER_MAX_GAP_MS = 35;

export interface BarcodeScannerOptions {
  onScan: (barcode: string) => void;
  /** Optional GS1-aware callback with structured AI 01/10/17 fields from one capture. */
  onGs1Scan?: (payload: {
    raw: string;
    gtin: string | null;
    lot: string | null;
    expiry: string | null;
    serial: string | null;
  }) => void;
  prefix?: string;
  suffix?: string;
  captureAll?: boolean;
  enabled?: boolean;
}

function isEditableElement(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  return target.isContentEditable;
}

function stripAffixes(
  value: string,
  prefix?: string,
  suffix?: string
): string {
  let result = value;
  if (prefix && result.startsWith(prefix)) {
    result = result.slice(prefix.length);
  }
  if (suffix && result.endsWith(suffix)) {
    result = result.slice(0, -suffix.length);
  }
  return result;
}

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
  const extras = obj.extras ?? (obj.intent as Record<string, unknown> | undefined)?.extras;
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
 * HID keyboard wedge + background intent broadcasting (DataWedge / Honeywell).
 * Intent listeners do not require focused input elements — glove-friendly floor ops.
 */
export function useBarcodeScanner({
  onScan,
  onGs1Scan,
  prefix,
  suffix,
  captureAll = false,
  enabled = true,
}: BarcodeScannerOptions): void {
  const bufferRef = useRef('');
  const lastKeyTimeRef = useRef(0);
  const onScanRef = useRef(onScan);
  const onGs1ScanRef = useRef(onGs1Scan);
  const { append, reset, commit } = useScanBufferStore();

  useEffect(() => {
    onScanRef.current = onScan;
  }, [onScan]);

  useEffect(() => {
    onGs1ScanRef.current = onGs1Scan;
  }, [onGs1Scan]);

  const ingestCommitted = useCallback(
    (barcode: string) => {
      const cleaned = barcode.trim();
      if (!cleaned) return;
      reset();
      commit(cleaned);
      const gs1 = parseGs1Barcode(cleaned);
      if (gs1 && onGs1ScanRef.current) {
        onGs1ScanRef.current({
          raw: gs1.raw,
          gtin: gs1.gtin,
          lot: gs1.lot,
          expiry: gs1.expiry,
          serial: gs1.serial,
        });
      }
      onScanRef.current(cleaned);
    },
    [commit, reset]
  );

  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (!enabled) return;
      if (!captureAll && isEditableElement(event.target)) return;

      const now = performance.now();
      const gap = now - lastKeyTimeRef.current;
      lastKeyTimeRef.current = now;

      if (event.key === 'Enter') {
        if (bufferRef.current.length > 0) {
          event.preventDefault();
          const raw = bufferRef.current;
          const barcode = stripAffixes(raw, prefix, suffix);
          bufferRef.current = '';
          ingestCommitted(barcode);
        }
        return;
      }

      if (event.key.length !== 1) return;

      if (bufferRef.current.length === 0 || gap < SCANNER_MAX_GAP_MS) {
        event.preventDefault();
        bufferRef.current += event.key;
        append(event.key);
      } else {
        bufferRef.current = event.key;
        reset();
        append(event.key);
      }
    },
    [enabled, captureAll, prefix, suffix, append, reset, ingestCommitted]
  );

  const handleNativeScan = useCallback(
    (event: Event) => {
      if (!enabled) return;
      const custom = event as CustomEvent<unknown>;
      const barcode = extractIntentBarcode(custom.detail) ?? extractIntentBarcode((event as MessageEvent).data);
      if (barcode) {
        ingestCommitted(stripAffixes(barcode, prefix, suffix));
      }
    },
    [enabled, ingestCommitted, prefix, suffix]
  );

  const handleMessage = useCallback(
    (event: MessageEvent) => {
      if (!enabled) return;
      const barcode = extractIntentBarcode(event.data);
      if (barcode) {
        ingestCommitted(stripAffixes(barcode, prefix, suffix));
      }
    },
    [enabled, ingestCommitted, prefix, suffix]
  );

  useEffect(() => {
    if (!enabled) return;
    window.addEventListener('keydown', handleKeyDown, true);
    for (const name of INTENT_EVENT_NAMES) {
      window.addEventListener(name, handleNativeScan as EventListener);
    }
    window.addEventListener('message', handleMessage);
    if (Capacitor.isNativePlatform()) {
      // hardwareScan already registered above for Capacitor bridges
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
      for (const name of INTENT_EVENT_NAMES) {
        window.removeEventListener(name, handleNativeScan as EventListener);
      }
      window.removeEventListener('message', handleMessage);
    };
  }, [enabled, handleKeyDown, handleNativeScan, handleMessage]);
}

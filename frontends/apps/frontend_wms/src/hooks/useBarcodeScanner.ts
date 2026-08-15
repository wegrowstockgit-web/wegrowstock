import { useCallback, useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { createScanEventPayload, type ScanEventPayload } from '@/offline/scanEvent';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { parseGs1, type ParsedBarcode } from '@/utils/gs1Parser';

const SCANNER_MAX_GAP_MS = 35;

export type { ScanEventPayload };

export interface BarcodeScannerOptions {
  /**
   * Legacy barcode callback. Prefer {@link onScanEvent} when the consumer needs
   * a client `idempotencyKey` + high-res `scannedAt` for offline queueing.
   */
  onScan: (barcode: string, parsed?: ParsedBarcode) => void;
  /**
   * Fired for every committed hardware / wedge scan with a fully formed
   * {@link ScanEventPayload} (UUIDv4 idempotency key + high-res timestamp).
   */
  onScanEvent?: (event: ScanEventPayload) => void;
  /**
   * Fired when a composite GS1 payload is decoded — before / instead of dumping
   * the raw AI string into the scan buffer. Use to auto-fill Lot / Expiry / Qty.
   */
  onGs1Scan?: (parsed: ParsedBarcode) => void;
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
  'intent',
] as const;

/** Cordova / Capacitor Intent Shim (Zebra DataWedge + Honeywell) on Android WebView. */
type IntentShim = {
  registerBroadcastReceiver?: (
    filters: { filterActions: string[]; filterCategories?: string[] },
    callback: (intent: unknown) => void,
  ) => void;
  unregisterBroadcastReceiver?: () => void;
};

function getIntentShim(): IntentShim | undefined {
  const w = window as Window & {
    plugins?: { intentShim?: IntentShim };
    intentShim?: IntentShim;
  };
  return w.plugins?.intentShim ?? w.intentShim;
}

const DATAWEDGE_ACTIONS = [
  'com.symbol.datawedge.api.RESULT_ACTION',
  'com.symbol.datawedge.data',
  'com.honeywell.sample.action.BARCODE_DATA',
  'com.honeywell.decode.intent.action.EDIT_DATA',
] as const;

/**
 * HID keyboard wedge + background intent broadcasting (DataWedge / Honeywell).
 * Intent listeners do not require focused input elements — glove-friendly floor ops.
 *
 * GS1 composites are parsed instantly client-side: the scan buffer receives the
 * GTIN/SKU lookup key (not the raw AI string), and structured fields are emitted
 * via {@link BarcodeScannerOptions.onGs1Scan}.
 */
export function useBarcodeScanner({
  onScan,
  onScanEvent,
  onGs1Scan,
  prefix,
  suffix,
  captureAll = false,
  enabled = true,
}: BarcodeScannerOptions): void {
  const bufferRef = useRef('');
  const lastKeyTimeRef = useRef(0);
  const onScanRef = useRef(onScan);
  const onScanEventRef = useRef(onScanEvent);
  const onGs1ScanRef = useRef(onGs1Scan);
  const { append, reset, commit } = useScanBufferStore();

  useEffect(() => {
    onScanRef.current = onScan;
  }, [onScan]);

  useEffect(() => {
    onScanEventRef.current = onScanEvent;
  }, [onScanEvent]);

  useEffect(() => {
    onGs1ScanRef.current = onGs1Scan;
  }, [onGs1Scan]);

  const ingestCommitted = useCallback(
    (barcode: string) => {
      const cleaned = barcode.trim();
      if (!cleaned) return;
      reset();

      const parsed = parseGs1(cleaned);
      if (parsed.isGs1) {
        // Intercept: commit GTIN/SKU to the buffer — never the composite AI blob.
        commit(parsed.sku);
        onGs1ScanRef.current?.(parsed);
        const event = createScanEventPayload(parsed.sku, parsed);
        onScanEventRef.current?.(event);
        onScanRef.current(event.barcode, event.parsed);
        return;
      }

      commit(cleaned);
      const event = createScanEventPayload(cleaned, parsed);
      onScanEventRef.current?.(event);
      onScanRef.current(event.barcode, event.parsed);
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
    if (!enabled) {
      return undefined;
    }

    const onKeyDown = handleKeyDown;
    const onNativeScan = handleNativeScan as EventListener;
    const onMessage = handleMessage;

    window.addEventListener('keydown', onKeyDown, true);
    for (const name of INTENT_EVENT_NAMES) {
      window.addEventListener(name, onNativeScan);
    }
    window.addEventListener('message', onMessage);

    // Native Android laser wedge via cordova-plugin-intent / intentShim — no focused input required.
    const shim = getIntentShim();
    const onIntent = (intent: unknown) => {
      const barcode =
        extractIntentBarcode(intent) ??
        extractIntentBarcode((intent as { extras?: unknown } | null)?.extras);
      if (barcode) {
        ingestCommitted(stripAffixes(barcode, prefix, suffix));
      }
    };
    if (shim?.registerBroadcastReceiver) {
      try {
        shim.registerBroadcastReceiver(
          { filterActions: [...DATAWEDGE_ACTIONS], filterCategories: ['android.intent.category.DEFAULT'] },
          onIntent,
        );
      } catch {
        // Plugin absent or WebView without native bridge — HID + custom events still work.
      }
    }
    if (Capacitor.isNativePlatform()) {
      // hardwareScan already registered above for Capacitor bridges
    }

    return () => {
      window.removeEventListener('keydown', onKeyDown, true);
      for (const name of INTENT_EVENT_NAMES) {
        window.removeEventListener(name, onNativeScan);
      }
      window.removeEventListener('message', onMessage);
      try {
        shim?.unregisterBroadcastReceiver?.();
      } catch {
        // ignore
      }
    };
  }, [enabled, handleKeyDown, handleNativeScan, handleMessage, ingestCommitted, prefix, suffix]);
}

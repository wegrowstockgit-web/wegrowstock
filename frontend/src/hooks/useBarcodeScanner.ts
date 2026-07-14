import { useCallback, useEffect, useRef } from 'react';
import { useScanBufferStore } from '@/stores/scanBuffer';

const SCANNER_MAX_GAP_MS = 35;

export interface BarcodeScannerOptions {
  onScan: (barcode: string) => void;
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

export function useBarcodeScanner({
  onScan,
  prefix,
  suffix,
  captureAll = false,
  enabled = true,
}: BarcodeScannerOptions): void {
  const bufferRef = useRef('');
  const lastKeyTimeRef = useRef(0);
  const onScanRef = useRef(onScan);
  const { append, reset, commit } = useScanBufferStore();

  useEffect(() => {
    onScanRef.current = onScan;
  }, [onScan]);

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
          reset();
          commit(barcode);
          onScanRef.current(barcode);
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
    [enabled, captureAll, prefix, suffix, append, reset, commit]
  );

  useEffect(() => {
    if (!enabled) return;
    window.addEventListener('keydown', handleKeyDown, true);
    return () => window.removeEventListener('keydown', handleKeyDown, true);
  }, [enabled, handleKeyDown]);
}

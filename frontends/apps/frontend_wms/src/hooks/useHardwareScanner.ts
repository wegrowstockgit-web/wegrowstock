import {
  useBarcodeScanner,
  type BarcodeScannerOptions,
  type ScanEventPayload,
  extractIntentBarcode,
} from '@/hooks/useBarcodeScanner';

export { extractIntentBarcode };
export type { ScanEventPayload };

/**
 * Alias for useBarcodeScanner — HID wedge + DataWedge / Honeywell intent broadcasting.
 * Every committed scan constructs a {@link ScanEventPayload} (idempotency key + scannedAt).
 */
export function useHardwareScanner(options: BarcodeScannerOptions): void {
  useBarcodeScanner(options);
}

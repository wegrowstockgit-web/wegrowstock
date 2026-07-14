import { useBarcodeScanner, type BarcodeScannerOptions, extractIntentBarcode } from '@/hooks/useBarcodeScanner';

export { extractIntentBarcode };

/**
 * Alias for useBarcodeScanner — HID wedge + DataWedge / Honeywell intent broadcasting.
 */
export function useHardwareScanner(options: BarcodeScannerOptions): void {
  useBarcodeScanner(options);
}

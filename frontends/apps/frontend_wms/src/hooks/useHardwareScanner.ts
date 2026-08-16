import {
  useBarcodeScanner,
  type BarcodeScannerOptions,
  type ScanEventPayload,
  extractIntentBarcode,
} from '@/hooks/useBarcodeScanner';
import { getHardwareCapabilities } from '@/lib/hardwareCapabilities';

export { extractIntentBarcode };
export type { ScanEventPayload };

export interface UseHardwareScannerResult {
  isSupported: boolean;
  isBluetoothSupported: boolean;
  isSerialSupported: boolean;
}

/**
 * HID wedge + DataWedge / Honeywell intent broadcasting.
 * Web Serial / Web Bluetooth are probed without throwing so Safari and Firefox
 * can keep using keyboard-wedge scans and a typed fallback.
 */
export function useHardwareScanner(options: BarcodeScannerOptions): UseHardwareScannerResult {
  const { isSupported, isBluetoothSupported, isSerialSupported } = getHardwareCapabilities();
  useBarcodeScanner(options);
  return { isSupported, isBluetoothSupported, isSerialSupported };
}

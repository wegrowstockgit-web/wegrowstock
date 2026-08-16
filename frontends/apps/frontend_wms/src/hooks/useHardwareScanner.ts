import {
  useBarcodeScanner,
  type BarcodeScannerOptions,
  type ScanEventPayload,
  type UseBarcodeScannerResult,
  extractIntentBarcode,
} from '@/hooks/useBarcodeScanner';
import { getHardwareCapabilities } from '@/lib/hardwareCapabilities';

export { extractIntentBarcode };
export type { ScanEventPayload };

export interface UseHardwareScannerResult extends UseBarcodeScannerResult {
  isSupported: boolean;
  isBluetoothSupported: boolean;
  isSerialSupported: boolean;
}

/**
 * HID wedge + DataWedge / Honeywell intent broadcasting.
 * Web Serial / Web Bluetooth are probed without throwing so Safari and Firefox
 * can keep using keyboard-wedge scans, device camera, and a typed fallback.
 */
export function useHardwareScanner(options: BarcodeScannerOptions): UseHardwareScannerResult {
  const { isSupported, isBluetoothSupported, isSerialSupported } = getHardwareCapabilities();
  const scanner = useBarcodeScanner(options);
  return { ...scanner, isSupported, isBluetoothSupported, isSerialSupported };
}

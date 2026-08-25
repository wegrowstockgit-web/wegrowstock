export type HardwareStatus = 'CONNECTED' | 'DISCONNECTED' | 'UNSUPPORTED';

export interface HardwareCapabilities {
  isBluetoothSupported: boolean;
  isSerialSupported: boolean;
  /** True when Web Bluetooth or Web Serial is present (Chromium). False on Safari/Firefox. */
  isSupported: boolean;
}

export const UNSUPPORTED_BROWSER_HARDWARE_MESSAGE =
  'Your current browser does not support direct hardware connections. To connect USB or Bluetooth scales, please use Google Chrome or Microsoft Edge on a Windows, Mac, or Android device.';

/**
 * Live probe — read at call time so tests can stub navigator after import.
 * HID keyboard-wedge barcode scanners do not depend on these APIs.
 */
export function supportsWebSerial(
  nav: Navigator | undefined = typeof navigator === 'undefined' ? undefined : navigator,
): boolean {
  return Boolean(nav && 'serial' in nav);
}

export function supportsWebBluetooth(
  nav: Navigator | undefined = typeof navigator === 'undefined' ? undefined : navigator,
): boolean {
  return Boolean(nav && 'bluetooth' in nav);
}

/**
 * Capability probe for Web Hardware APIs. Never throws — Safari and Firefox
 * omit `navigator.bluetooth` / `navigator.serial` entirely.
 */
export function getHardwareCapabilities(
  nav: Navigator | undefined = typeof navigator === 'undefined' ? undefined : navigator,
): HardwareCapabilities {
  const isBluetoothSupported = supportsWebBluetooth(nav);
  const isSerialSupported = supportsWebSerial(nav);
  return {
    isBluetoothSupported,
    isSerialSupported,
    isSupported: isBluetoothSupported || isSerialSupported,
  };
}

/**
 * Native DataWedge / Honeywell / Capacitor bridges count as a live hardware path.
 */
export function hasNativeScanBridge(
  win: Window | undefined = typeof window === 'undefined' ? undefined : window,
): boolean {
  if (!win) return false;
  const w = win as Window & {
    plugins?: { intentShim?: { registerBroadcastReceiver?: unknown } };
    intentShim?: unknown;
    Capacitor?: { isNativePlatform?: () => boolean };
  };
  if (w.Capacitor?.isNativePlatform?.()) return true;
  return Boolean(w.plugins?.intentShim?.registerBroadcastReceiver || w.intentShim);
}

/**
 * Probe Web Serial / Bluetooth plus native scan bridges.
 * {@code connected} is true after a confirmed hardware/HID ingest.
 */
export function resolveHardwareStatus(
  nav: Navigator | undefined = typeof navigator === 'undefined' ? undefined : navigator,
  options: { connected?: boolean; nativeBridge?: boolean } = {},
): HardwareStatus {
  if (options.nativeBridge ?? hasNativeScanBridge()) {
    return 'CONNECTED';
  }
  const caps = getHardwareCapabilities(nav);
  if (!caps.isSupported) {
    return 'UNSUPPORTED';
  }
  return options.connected ? 'CONNECTED' : 'DISCONNECTED';
}

export type HardwareStatus = 'CONNECTED' | 'DISCONNECTED' | 'UNSUPPORTED';

export interface HardwareCapabilities {
  isBluetoothSupported: boolean;
  isSerialSupported: boolean;
  /** True when Web Bluetooth or Web Serial is present (Chromium). False on Safari/Firefox. */
  isSupported: boolean;
}

/**
 * Capability probe for Web Hardware APIs. Never throws — Safari and Firefox
 * omit `navigator.bluetooth` / `navigator.serial` entirely.
 */
export function getHardwareCapabilities(
  nav: Navigator | undefined = typeof navigator === 'undefined' ? undefined : navigator,
): HardwareCapabilities {
  const isBluetoothSupported = Boolean(nav && 'bluetooth' in nav);
  const isSerialSupported = Boolean(nav && 'serial' in nav);
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

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

import { describe, expect, it } from 'vitest';
import {
  getHardwareCapabilities,
  hasNativeScanBridge,
  resolveHardwareStatus,
  supportsWebBluetooth,
  supportsWebSerial,
  UNSUPPORTED_BROWSER_HARDWARE_MESSAGE,
} from './hardwareCapabilities';

describe('getHardwareCapabilities', () => {
  it('returns isSupported false when bluetooth and serial are missing', () => {
    const nav = {} as Navigator;
    expect(getHardwareCapabilities(nav)).toEqual({
      isBluetoothSupported: false,
      isSerialSupported: false,
      isSupported: false,
    });
  });

  it('detects Web Bluetooth without throwing', () => {
    const nav = { bluetooth: {} } as unknown as Navigator;
    const caps = getHardwareCapabilities(nav);
    expect(caps.isBluetoothSupported).toBe(true);
    expect(caps.isSupported).toBe(true);
  });

  it('detects Web Serial without throwing', () => {
    const nav = { serial: {} } as unknown as Navigator;
    const caps = getHardwareCapabilities(nav);
    expect(caps.isSerialSupported).toBe(true);
    expect(caps.isSupported).toBe(true);
  });

  it('is safe when navigator is undefined', () => {
    expect(getHardwareCapabilities(undefined).isSupported).toBe(false);
  });
});

describe('supportsWebSerial / supportsWebBluetooth', () => {
  it('mirrors getHardwareCapabilities probes', () => {
    expect(supportsWebSerial({} as Navigator)).toBe(false);
    expect(supportsWebBluetooth({} as Navigator)).toBe(false);
    expect(supportsWebSerial({ serial: {} } as unknown as Navigator)).toBe(true);
    expect(supportsWebBluetooth({ bluetooth: {} } as unknown as Navigator)).toBe(true);
  });

  it('documents the Safari / iPad recovery copy', () => {
    expect(UNSUPPORTED_BROWSER_HARDWARE_MESSAGE).toContain('Google Chrome or Microsoft Edge');
    expect(UNSUPPORTED_BROWSER_HARDWARE_MESSAGE).toContain('USB or Bluetooth scales');
  });
});

describe('resolveHardwareStatus', () => {
  it('is UNSUPPORTED when serial and bluetooth are missing', () => {
    expect(resolveHardwareStatus({} as Navigator)).toBe('UNSUPPORTED');
  });

  it('is DISCONNECTED when Web Serial exists but no device has scanned', () => {
    expect(resolveHardwareStatus({ serial: {} } as unknown as Navigator)).toBe('DISCONNECTED');
  });

  it('is CONNECTED after a confirmed hardware ingest on Chromium', () => {
    expect(
      resolveHardwareStatus({ serial: {} } as unknown as Navigator, { connected: true }),
    ).toBe('CONNECTED');
  });

  it('is CONNECTED when a native scan bridge is present', () => {
    expect(resolveHardwareStatus({} as Navigator, { nativeBridge: true })).toBe('CONNECTED');
  });

  it('hasNativeScanBridge is false without plugins', () => {
    expect(hasNativeScanBridge(window)).toBe(false);
  });
});

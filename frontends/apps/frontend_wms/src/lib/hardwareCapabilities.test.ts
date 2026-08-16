import { describe, expect, it } from 'vitest';
import { getHardwareCapabilities } from './hardwareCapabilities';

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

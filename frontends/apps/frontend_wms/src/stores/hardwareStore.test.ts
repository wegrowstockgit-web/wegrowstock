import { beforeEach, describe, expect, it } from 'vitest';
import { isAnyScaleConnected, useHardwareStore } from './hardwareStore';

describe('hardwareStore', () => {
  beforeEach(() => {
    useHardwareStore.getState().resetHardwareStore();
  });

  it('starts with every worker device disconnected', () => {
    const state = useHardwareStore.getState();
    expect(state.bluetoothScale).toEqual({ connected: false, deviceName: null });
    expect(state.serialScale).toEqual({ connected: false, portInfo: null });
    expect(state.qzTray).toEqual({ connected: false, version: null });
    expect(state.managerOpen).toBe(false);
    expect(isAnyScaleConnected(state)).toBe(false);
  });

  it('tracks bluetooth, serial, and QZ Tray links independently', () => {
    const { setBluetoothScale, setSerialScale, setQzTray } = useHardwareStore.getState();
    setBluetoothScale({ connected: true, deviceName: 'Acme Scale' });
    setSerialScale({ connected: true, portInfo: 'USB serial · 9600 baud' });
    setQzTray({ connected: true, version: 'QZ Tray' });

    const state = useHardwareStore.getState();
    expect(state.bluetoothScale.deviceName).toBe('Acme Scale');
    expect(state.serialScale.portInfo).toContain('9600');
    expect(state.qzTray.version).toBe('QZ Tray');
    expect(isAnyScaleConnected(state)).toBe(true);
  });

  it('opens and closes the Device Manager modal', () => {
    useHardwareStore.getState().openManager();
    expect(useHardwareStore.getState().managerOpen).toBe(true);
    useHardwareStore.getState().closeManager();
    expect(useHardwareStore.getState().managerOpen).toBe(false);
  });

  it('clears live scale links when a disconnect is requested', () => {
    useHardwareStore.getState().setBluetoothScale({ connected: true, deviceName: 'Scale' });
    useHardwareStore.getState().requestScaleDisconnect();
    const state = useHardwareStore.getState();
    expect(state.bluetoothScale.connected).toBe(false);
    expect(state.scaleDisconnectToken).toBe(1);
    expect(state.scaleReading).toBeNull();
  });
});

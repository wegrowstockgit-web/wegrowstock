import { create } from 'zustand';
import type { DigitalScaleReading } from '@/hooks/useDigitalScale';

export interface BluetoothScaleLink {
  connected: boolean;
  deviceName: string | null;
}

export interface SerialScaleLink {
  connected: boolean;
  portInfo: string | null;
}

export interface QzTrayLink {
  connected: boolean;
  version: string | null;
}

export interface HardwareStoreState {
  bluetoothScale: BluetoothScaleLink;
  serialScale: SerialScaleLink;
  qzTray: QzTrayLink;
  scaleReading: DigitalScaleReading | null;
  managerOpen: boolean;
  /** Incremented so the mounted Device Manager can drop the live Web Serial / GATT session. */
  scaleDisconnectToken: number;
  setBluetoothScale: (bluetoothScale: BluetoothScaleLink) => void;
  setSerialScale: (serialScale: SerialScaleLink) => void;
  setQzTray: (qzTray: QzTrayLink) => void;
  setScaleReading: (scaleReading: DigitalScaleReading | null) => void;
  openManager: () => void;
  closeManager: () => void;
  requestScaleDisconnect: () => void;
  resetHardwareStore: () => void;
}

const INITIAL_LINKS = {
  bluetoothScale: { connected: false, deviceName: null } satisfies BluetoothScaleLink,
  serialScale: { connected: false, portInfo: null } satisfies SerialScaleLink,
  qzTray: { connected: false, version: null } satisfies QzTrayLink,
  scaleReading: null as DigitalScaleReading | null,
  managerOpen: false,
  scaleDisconnectToken: 0,
};

export const useHardwareStore = create<HardwareStoreState>((set) => ({
  ...INITIAL_LINKS,
  setBluetoothScale: (bluetoothScale) => set({ bluetoothScale }),
  setSerialScale: (serialScale) => set({ serialScale }),
  setQzTray: (qzTray) => set({ qzTray }),
  setScaleReading: (scaleReading) => set({ scaleReading }),
  openManager: () => set({ managerOpen: true }),
  closeManager: () => set({ managerOpen: false }),
  requestScaleDisconnect: () =>
    set((state) => ({
      scaleDisconnectToken: state.scaleDisconnectToken + 1,
      bluetoothScale: { connected: false, deviceName: null },
      serialScale: { connected: false, portInfo: null },
      scaleReading: null,
    })),
  resetHardwareStore: () => set({ ...INITIAL_LINKS }),
}));

export function isAnyScaleConnected(state: Pick<HardwareStoreState, 'bluetoothScale' | 'serialScale'>): boolean {
  return state.bluetoothScale.connected || state.serialScale.connected;
}

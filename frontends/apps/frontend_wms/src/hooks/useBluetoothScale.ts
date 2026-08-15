import { useCallback, useEffect, useState } from 'react';

export interface BluetoothScaleReading {
  weightOz: number;
  weightLb: number;
  rawValue: string;
}

export interface UseBluetoothScaleResult {
  supported: boolean;
  connected: boolean;
  reading: BluetoothScaleReading | null;
  error: string | null;
  connecting: boolean;
  connect: () => Promise<void>;
  disconnect: () => void;
}

const WEIGHT_SCALE_SERVICE = '0000181d-0000-1000-8000-00805f9b34fb';
const WEIGHT_MEASUREMENT_CHAR = '00002a9d-0000-1000-8000-00805f9b34fb';

function parseWeightMeasurement(value: DataView): BluetoothScaleReading | null {
  const flags = value.getUint8(0);
  const isKg = (flags & 0x01) === 0;
  const raw = value.getUint16(1, true);
  const weightKg = isKg ? raw / 200 : raw / 200 * 0.453592;
  const weightLb = weightKg * 2.20462;
  const weightOz = weightLb * 16;
  return {
    weightOz,
    weightLb,
    rawValue: `${weightLb.toFixed(2)} lb`,
  };
}

export function useBluetoothScale(): UseBluetoothScaleResult {
  const supported =
    typeof navigator !== 'undefined' && 'bluetooth' in navigator && navigator.bluetooth != null;

  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [reading, setReading] = useState<BluetoothScaleReading | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [device, setDevice] = useState<BluetoothDevice | null>(null);

  const disconnect = useCallback(() => {
    if (device?.gatt?.connected) {
      device.gatt.disconnect();
    }
    setConnected(false);
    setDevice(null);
    setReading(null);
  }, [device]);

  const connect = useCallback(async () => {
    if (!supported) {
      setError('Web Bluetooth is not available in this browser.');
      return;
    }
    setConnecting(true);
    setError(null);
    try {
      const selected = await navigator.bluetooth!.requestDevice({
        filters: [{ services: [WEIGHT_SCALE_SERVICE] }],
        optionalServices: [WEIGHT_SCALE_SERVICE],
      });
      const gatt = await selected.gatt?.connect();
      if (!gatt) {
        throw new Error('Could not connect to scale GATT server');
      }
      const service = await gatt.getPrimaryService(WEIGHT_SCALE_SERVICE);
      const characteristic = await service.getCharacteristic(WEIGHT_MEASUREMENT_CHAR);
      await characteristic.startNotifications();
      characteristic.addEventListener('characteristicvaluechanged', (event: Event) => {
        const target = event.target as BluetoothRemoteGATTCharacteristic;
        const value = target.value;
        if (!value) return;
        const parsed = parseWeightMeasurement(value);
        if (parsed) setReading(parsed);
      });
      setDevice(selected);
      setConnected(true);
      selected.addEventListener('gattserverdisconnected', () => {
        setConnected(false);
        setReading(null);
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to connect to scale';
      if (!message.includes('cancelled')) {
        setError(message);
      }
    } finally {
      setConnecting(false);
    }
  }, [supported]);

  useEffect(() => () => disconnect(), [disconnect]);

  return {
    supported,
    connected,
    reading,
    error,
    connecting,
    connect,
    disconnect,
  };
}

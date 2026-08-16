import { useCallback, useEffect, useRef, useState } from 'react';
import { useBluetoothScale, type BluetoothScaleReading } from '@/hooks/useBluetoothScale';

export type ScaleTransport = 'bluetooth' | 'serial' | null;

export interface DigitalScaleReading {
  weightOz: number;
  weightLb: number;
  rawValue: string;
  transport: ScaleTransport;
  stable: boolean;
}

export interface UseDigitalScaleResult {
  supported: boolean;
  isSupported: boolean;
  bluetoothSupported: boolean;
  serialSupported: boolean;
  connected: boolean;
  transport: ScaleTransport;
  reading: DigitalScaleReading | null;
  error: string | null;
  connecting: boolean;
  connectBluetooth: () => Promise<void>;
  connectSerial: () => Promise<void>;
  disconnect: () => void;
}

/** Exported for unit tests — parses Toledo / Mettler-style ASCII scale lines. */
export function parseSerialWeightLine(line: string): BluetoothScaleReading | null {
  const trimmed = line.trim();
  if (!trimmed) return null;
  // Common shipping-bay formats: "  12.34 lb", "ST,GS,+0012.340lb", "12.34KG"
  const lb = trimmed.match(/([+-]?\d+(?:\.\d+)?)\s*(?:lb|lbs)\b/i);
  if (lb) {
    const weightLb = Number(lb[1]);
    if (!Number.isFinite(weightLb)) return null;
    return { weightLb, weightOz: weightLb * 16, rawValue: trimmed };
  }
  const kg = trimmed.match(/([+-]?\d+(?:\.\d+)?)\s*(?:kg|kgs)\b/i);
  if (kg) {
    const weightKg = Number(kg[1]);
    if (!Number.isFinite(weightKg)) return null;
    const weightLb = weightKg * 2.20462;
    return { weightLb, weightOz: weightLb * 16, rawValue: trimmed };
  }
  const bare = trimmed.match(/^[ST,]*.*?([+-]?\d+\.\d{1,3})/);
  if (bare) {
    const weightLb = Number(bare[1]);
    if (!Number.isFinite(weightLb) || weightLb <= 0) return null;
    return { weightLb, weightOz: weightLb * 16, rawValue: trimmed };
  }
  return null;
}

/**
 * Edge shipping-bay scale: Web Bluetooth GATT Weight Scale and/or Web Serial
 * continuous ASCII weight stream (Toledo / Mettler-style).
 */
export function useDigitalScale(): UseDigitalScaleResult {
  const bluetooth = useBluetoothScale();
  const serialSupported =
    typeof navigator !== 'undefined' && 'serial' in navigator && navigator.serial != null;

  const [serialConnected, setSerialConnected] = useState(false);
  const [serialReading, setSerialReading] = useState<BluetoothScaleReading | null>(null);
  const [serialError, setSerialError] = useState<string | null>(null);
  const [serialConnecting, setSerialConnecting] = useState(false);
  const [stable, setStable] = useState(false);
  const portRef = useRef<SerialPort | null>(null);
  const readerRef = useRef<ReadableStreamDefaultReader<string> | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const lastLbRef = useRef<number | null>(null);
  const stableSinceRef = useRef<number | null>(null);

  const disconnectSerial = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    void readerRef.current?.cancel().catch(() => undefined);
    readerRef.current = null;
    const port = portRef.current;
    portRef.current = null;
    if (port) {
      void port.close().catch(() => undefined);
    }
    setSerialConnected(false);
    setSerialReading(null);
  }, []);

  const disconnect = useCallback(() => {
    disconnectSerial();
    bluetooth.disconnect();
  }, [bluetooth, disconnectSerial]);

  const connectSerial = useCallback(async () => {
    if (!serialSupported) {
      setSerialError('Web Serial is not available in this browser.');
      return;
    }
    setSerialConnecting(true);
    setSerialError(null);
    try {
      bluetooth.disconnect();
      const port = await navigator.serial!.requestPort();
      await port.open({ baudRate: 9600 });
      portRef.current = port;
      setSerialConnected(true);
      const decoder = new TextDecoderStream();
      const abort = new AbortController();
      abortRef.current = abort;
      void port.readable!.pipeTo(decoder.writable, { signal: abort.signal }).catch(() => undefined);
      const reader = decoder.readable.getReader();
      readerRef.current = reader;
      let buffer = '';
      void (async () => {
        try {
          while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += value ?? '';
            const parts = buffer.split(/\r?\n/);
            buffer = parts.pop() ?? '';
            for (const line of parts) {
              const parsed = parseSerialWeightLine(line);
              if (parsed) setSerialReading(parsed);
            }
          }
        } catch {
          // aborted / disconnected
        } finally {
          setSerialConnected(false);
        }
      })();
    } catch (err) {
      setSerialError(err instanceof Error ? err.message : 'Serial scale connection failed');
      disconnectSerial();
    } finally {
      setSerialConnecting(false);
    }
  }, [bluetooth, disconnectSerial, serialSupported]);

  const activeReading = serialConnected ? serialReading : bluetooth.reading;
  const transport: ScaleTransport = serialConnected
    ? 'serial'
    : bluetooth.connected
      ? 'bluetooth'
      : null;

  useEffect(() => {
    const lb = activeReading?.weightLb ?? null;
    if (lb == null || lb <= 0) {
      setStable(false);
      lastLbRef.current = null;
      stableSinceRef.current = null;
      return;
    }
    const prev = lastLbRef.current;
    const now = Date.now();
    if (prev != null && Math.abs(prev - lb) < 0.02) {
      if (stableSinceRef.current == null) stableSinceRef.current = now;
      setStable(now - (stableSinceRef.current ?? now) >= 400);
    } else {
      lastLbRef.current = lb;
      stableSinceRef.current = now;
      setStable(false);
    }
  }, [activeReading?.weightLb]);

  return {
    supported: bluetooth.supported || serialSupported,
    isSupported: bluetooth.isSupported || serialSupported,
    bluetoothSupported: bluetooth.supported,
    serialSupported,
    connected: serialConnected || bluetooth.connected,
    transport,
    reading: activeReading
      ? {
          weightOz: activeReading.weightOz,
          weightLb: activeReading.weightLb,
          rawValue: activeReading.rawValue,
          transport,
          stable,
        }
      : null,
    error: serialError ?? bluetooth.error,
    connecting: serialConnecting || bluetooth.connecting,
    connectBluetooth: bluetooth.connect,
    connectSerial,
    disconnect,
  };
}

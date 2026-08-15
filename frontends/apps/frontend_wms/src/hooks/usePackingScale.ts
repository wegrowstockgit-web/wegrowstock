import { useCallback, useEffect, useRef, useState } from 'react';
import { parseSerialWeightLine } from '@/hooks/useDigitalScale';

export interface PackingScaleReading {
  weightLb: number;
  weightOz: number;
  rawValue: string;
  stable: boolean;
}

export interface UsePackingScaleResult {
  serialSupported: boolean;
  connected: boolean;
  connecting: boolean;
  reading: PackingScaleReading | null;
  error: string | null;
  /** Latest stable weight in lb suitable for pack-label mutation payload. */
  stableWeightLb: number | null;
  connect: () => Promise<void>;
  disconnect: () => void;
}

/**
 * Browser-native shipping-bay scale via Web Serial (9600 baud).
 * Streams ASCII weigh ticks through TextDecoderStream and exposes a stable
 * float for the packing shipment mutation — no desktop bridge required.
 */
export function usePackingScale(): UsePackingScaleResult {
  const serialSupported =
    typeof navigator !== 'undefined' && 'serial' in navigator && navigator.serial != null;

  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [reading, setReading] = useState<PackingScaleReading | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [stable, setStable] = useState(false);

  const portRef = useRef<SerialPort | null>(null);
  const readerRef = useRef<ReadableStreamDefaultReader<string> | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const lastLbRef = useRef<number | null>(null);
  const stableSinceRef = useRef<number | null>(null);

  const disconnect = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    void readerRef.current?.cancel().catch(() => undefined);
    readerRef.current = null;
    const port = portRef.current;
    portRef.current = null;
    if (port) {
      void port.close().catch(() => undefined);
    }
    setConnected(false);
    setReading(null);
    setStable(false);
    lastLbRef.current = null;
    stableSinceRef.current = null;
  }, []);

  const connect = useCallback(async () => {
    if (!serialSupported) {
      setError('Web Serial is not available in this browser.');
      return;
    }
    setConnecting(true);
    setError(null);
    try {
      const port = await navigator.serial!.requestPort();
      await port.open({ baudRate: 9600 });
      portRef.current = port;
      setConnected(true);

      const decoder = new TextDecoderStream();
      const abort = new AbortController();
      abortRef.current = abort;
      void port.readable!.pipeTo(decoder.writable, { signal: abort.signal }).catch(() => {
        setError('Scale serial stream disconnected');
        setConnected(false);
      });

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
              if (!parsed) continue;
              setReading({
                weightLb: parsed.weightLb,
                weightOz: parsed.weightOz,
                rawValue: parsed.rawValue,
                stable: false,
              });
            }
          }
        } catch {
          // aborted / device unplugged
        } finally {
          setConnected(false);
        }
      })();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Serial scale connection failed');
      disconnect();
    } finally {
      setConnecting(false);
    }
  }, [disconnect, serialSupported]);

  useEffect(() => {
    const lb = reading?.weightLb ?? null;
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
      const isStable = now - (stableSinceRef.current ?? now) >= 400;
      setStable(isStable);
      setReading((r) => (r ? { ...r, stable: isStable } : r));
    } else {
      lastLbRef.current = lb;
      stableSinceRef.current = now;
      setStable(false);
      setReading((r) => (r ? { ...r, stable: false } : r));
    }
  }, [reading?.weightLb]);

  useEffect(() => () => disconnect(), [disconnect]);

  return {
    serialSupported,
    connected,
    connecting,
    reading: reading ? { ...reading, stable } : null,
    error,
    stableWeightLb: stable && reading && reading.weightLb > 0 ? reading.weightLb : null,
    connect,
    disconnect,
  };
}

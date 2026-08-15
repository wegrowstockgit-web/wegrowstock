import { generateIdempotencyKey } from '@/lib/utils';
import type { ParsedBarcode } from '@/utils/gs1Parser';

/**
 * Client-authored scan event for offline-first floor ops.
 * `idempotencyKey` + high-res `scannedAt` travel with the mutation queue.
 */
export interface ScanEventPayload {
  barcode: string;
  /** UUIDv4 — stable across offline enqueue and online replay. */
  idempotencyKey: string;
  /** High-resolution epoch ms (`performance.timeOrigin + performance.now()`). */
  scannedAt: number;
  parsed?: ParsedBarcode;
  /** Original wedge payload when GS1 was decoded to a GTIN/SKU. */
  rawBarcode?: string;
}

/** Monotonic wall-clock ms suitable for chronological queue drain. */
export function highResTimestamp(): number {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    const origin =
      typeof performance.timeOrigin === 'number' && Number.isFinite(performance.timeOrigin)
        ? performance.timeOrigin
        : Date.now() - performance.now();
    return Math.round(origin + performance.now());
  }
  return Date.now();
}

export function createScanEventPayload(
  barcode: string,
  parsed?: ParsedBarcode,
): ScanEventPayload {
  const cleaned = barcode.trim();
  return {
    barcode: cleaned,
    idempotencyKey: generateIdempotencyKey(),
    scannedAt: highResTimestamp(),
    parsed,
    rawBarcode: parsed?.isGs1 ? (parsed.raw ?? cleaned) : cleaned,
  };
}
